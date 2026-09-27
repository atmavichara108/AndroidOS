package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.InboxKind
import ru.rudra.androidos.pa.domain.model.InboxState
import ru.rudra.androidos.pa.domain.model.RetentionClass

/**
 * Decodes a [Change] into an idempotent, self-contained materialization
 * command. The patch must be self-describing: the sender includes the full
 * state the receiver needs to recreate the row (see [Patches]). A change whose
 * patch lacks the required fields cannot be materialized and is reported as
 * [MaterializeOp.Unsupported] rather than guessed at.
 *
 * This class is pure (no Room/Android) so the semantics are unit-testable.
 */
sealed interface MaterializeOp {
    data class UpsertEntity(
        val id: String,
        val kind: String,
        val title: String,
        val status: String,
    ) : MaterializeOp

    data class UpsertInbox(
        val id: String,
        val kind: String,
        val state: String,
        val body: String?,
        val sourceDeviceId: String,
        val capturedAt: String,
        val createdAt: String,
        val updatedAt: String,
        val retentionClass: String,
        val transcriptId: String?,
    ) : MaterializeOp

    data class UpsertTranscript(
        val id: String,
        val inboxItemId: String,
        val text: String,
        val status: String,
    ) : MaterializeOp

    data class Tombstone(val id: String, val deletedAt: String) : MaterializeOp

    data class Unsupported(val changeId: String, val reason: String) : MaterializeOp
}

object ChangeMaterializer {

    fun materialize(change: Change): MaterializeOp = when (change.operation) {
        ChangeOperation.CREATE -> create(change)
        ChangeOperation.UPDATE -> update(change)
        ChangeOperation.TOMBSTONE -> tombstone(change)
        ChangeOperation.DELETE -> MaterializeOp.Unsupported(change.id, "DELETE not used by PA")
    }

    private fun create(change: Change): MaterializeOp {
        val p = change.patch
        val entityKind = p["kind"]
        return when {
            // inbox kinds are TEXT / AUDIO / IMPORT — these become inbox rows,
            // not entities.
            entityKind != null && entityKind in INBOX_KINDS -> MaterializeOp.UpsertInbox(
                id = change.entityId,
                kind = entityKind,
                state = p["state"] ?: InboxState.CAPTURED.name,
                body = p["body"],
                sourceDeviceId = p["sourceDeviceId"] ?: change.actorDeviceId,
                capturedAt = p["capturedAt"] ?: change.occurredAt,
                createdAt = p["createdAt"] ?: change.occurredAt,
                updatedAt = p["updatedAt"] ?: change.occurredAt,
                retentionClass = p["retentionClass"] ?: change.retentionClass.name,
                transcriptId = p["transcriptId"],
            )
            // entity path: approve() creates a TASK/EVENT entity from its title.
            entityKind != null -> MaterializeOp.UpsertEntity(
                id = change.entityId,
                kind = entityKind,
                title = p["title"] ?: "",
                status = p["status"] ?: "APPROVED",
            )
            // legacy capture() path: a bare body with no kind is a TEXT inbox row.
            p.containsKey("body") -> MaterializeOp.UpsertInbox(
                id = change.entityId,
                kind = InboxKind.TEXT.name,
                state = p["state"] ?: InboxState.CAPTURED.name,
                body = p["body"],
                sourceDeviceId = p["sourceDeviceId"] ?: change.actorDeviceId,
                capturedAt = p["capturedAt"] ?: change.occurredAt,
                createdAt = p["createdAt"] ?: change.occurredAt,
                updatedAt = p["updatedAt"] ?: change.occurredAt,
                retentionClass = p["retentionClass"] ?: change.retentionClass.name,
                transcriptId = p["transcriptId"],
            )
            else -> MaterializeOp.Unsupported(change.id, "CREATE with no kind and no body")
        }
    }

    private val INBOX_KINDS = setOf(
        InboxKind.TEXT.name,
        InboxKind.AUDIO.name,
        InboxKind.IMPORT.name,
    )

    private fun update(change: Change): MaterializeOp {
        val p = change.patch
        return when {
            // storeTranscript() / transcript-edit path.
            p.containsKey("transcriptId") || p.containsKey("transcriptText") -> {
                MaterializeOp.UpsertTranscript(
                    id = p["transcriptId"] ?: change.id,
                    inboxItemId = change.entityId,
                    text = p["transcriptText"] ?: "",
                    status = p["transcriptStatus"] ?: "RAW",
                )
            }
            // An entity title/status edit, but only for non-inbox kinds.
            p.containsKey("kind") && p["kind"] !in INBOX_KINDS -> MaterializeOp.UpsertEntity(
                id = change.entityId,
                kind = p["kind"] ?: "",
                title = p["title"] ?: "",
                status = p["status"] ?: "APPROVED",
            )
            else -> MaterializeOp.Unsupported(change.id, "UPDATE with no recognized fields")
        }
    }

    private fun tombstone(change: Change): MaterializeOp = MaterializeOp.Tombstone(
        id = change.entityId,
        deletedAt = change.patch["deletedAt"] ?: change.occurredAt,
    )
}

/** Canonical patch keys used by the senders (PA app + laptop peer). */
object Patches {
    const val KIND = "kind"
    const val TITLE = "title"
    const val STATUS = "status"
    const val BODY = "body"
    const val STATE = "state"
    const val SOURCE_DEVICE = "sourceDeviceId"
    const val CAPTURED_AT = "capturedAt"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val RETENTION = "retentionClass"
    const val TRANSCRIPT_ID = "transcriptId"
    const val TRANSCRIPT_TEXT = "transcriptText"
    const val TRANSCRIPT_STATUS = "transcriptStatus"
    const val DELETED_AT = "deletedAt"
}