package ru.rudra.androidos.pa.data

import org.json.JSONObject
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.port.LocalStore
import ru.rudra.androidos.pa.domain.sync.ChangeMaterializer
import ru.rudra.androidos.pa.domain.sync.MaterializeOp

/**
 * Room-backed [LocalStore]. Applying a change writes it to the append-only
 * `changes` log AND materializes it into the domain tables (entities, inbox,
 * transcripts) in the same transaction, so a change received from a peer
 * actually shows up in the UI. Materialization is driven by [ChangeMaterializer];
 * a change whose patch is not self-describing is logged but not guessed at.
 */
class RoomLocalStore(private val db: PaDatabase) : LocalStore {

    override fun applyChange(change: Change): Boolean {
        if (db.changeDao().exists(change.id, change.idempotencyKey)) {
            return false
        }
        db.runInTransaction {
            db.changeDao().insert(change.toRow())
            materialize(change)
        }
        return true
    }

    /** All local changes in canonical order — the input for envelope export. */
    fun allChanges(): List<Change> = db.changeDao().all().map { it.toChange() }

    private fun materialize(change: Change) {
        when (val op = ChangeMaterializer.materialize(change)) {
            is MaterializeOp.UpsertEntity -> {
                if (db.entityDao().byId(op.id) == null) {
                    db.entityDao().insert(
                        EntityRow(
                            id = op.id,
                            type = op.kind,
                            schemaVersion = 1,
                            attributesJson = JSONObject(mapOf("title" to op.title)).toString(),
                            status = op.status,
                            version = 1,
                            deletedAt = null,
                        )
                    )
                }
            }
            is MaterializeOp.UpsertInbox -> {
                if (db.inboxDao().byId(op.id) == null) {
                    db.inboxDao().insert(
                        InboxItemRow(
                            id = op.id,
                            kind = op.kind,
                            state = op.state,
                            transcriptId = op.transcriptId,
                            body = op.body,
                            sourceDeviceId = op.sourceDeviceId,
                            capturedAt = op.capturedAt,
                            createdAt = op.createdAt,
                            updatedAt = op.updatedAt,
                            retentionClass = op.retentionClass,
                            version = 1,
                            deletedAt = null,
                        )
                    )
                }
            }
            is MaterializeOp.UpsertTranscript -> {
                val existing = db.transcriptDao().forInboxItem(op.inboxItemId)
                    .firstOrNull { it.id == op.id }
                if (existing == null) {
                    db.transcriptDao().insert(
                        TranscriptRow(
                            id = op.id,
                            inboxItemId = op.inboxItemId,
                            text = op.text,
                            engineId = "sync-peer",
                            modelId = "peer",
                            status = op.status,
                            editedAt = null,
                            retentionClass = change.retentionClass.name,
                            version = 1,
                            deletedAt = null,
                        )
                    )
                    db.inboxDao().setTranscriptId(op.inboxItemId, op.id)
                }
                // A peer-originated transcript must also carry its inbox state
                // transition (e.g. CAPTURED -> TRANSCRIBED), which the sender
                // includes as patch["state"]. Apply it when present.
                change.patch["state"]?.let { state ->
                    val now = change.occurredAt
                    db.inboxDao().updateState(op.inboxItemId, state, now)
                }
            }
            is MaterializeOp.Tombstone -> {
                db.inboxDao().tombstone(op.id, op.deletedAt)
                db.entityDao().tombstone(op.id, op.deletedAt)
            }
            is MaterializeOp.Unsupported -> {
                android.util.Log.w("PA_SYNC", "skipped non-materializable change ${op.changeId}: ${op.reason}")
            }
        }
    }

    private fun Change.toRow(): ChangeRow = ChangeRow(
        id = id,
        entityId = entityId,
        operation = operation.name,
        patchJson = JSONObject(patch).toString(),
        actorDeviceId = actorDeviceId,
        baseVersion = baseVersion,
        occurredAt = occurredAt,
        idempotencyKey = idempotencyKey,
        retentionClass = retentionClass.name,
    )

    private fun ChangeRow.toChange(): Change {
        val patch = runCatching {
            val obj = JSONObject(patchJson)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
        return Change(
            id = id,
            entityId = entityId,
            operation = ChangeOperation.valueOf(operation),
            patch = patch,
            actorDeviceId = actorDeviceId,
            baseVersion = baseVersion,
            occurredAt = occurredAt,
            // ChangeRow does not persist logicalClock/provenance (P1 scope);
            // they are re-added by the sync layer in P2.
            logicalClock = null,
            idempotencyKey = idempotencyKey,
            provenance = emptyList(),
            retentionClass = RetentionClass.valueOf(retentionClass),
        )
    }
}