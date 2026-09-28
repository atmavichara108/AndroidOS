package ru.rudra.androidos.pa.data

import org.json.JSONObject
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.port.LocalStore
import ru.rudra.androidos.pa.domain.sync.ChangeMaterializer
import ru.rudra.androidos.pa.domain.sync.ConflictOutcome
import ru.rudra.androidos.pa.domain.sync.ConflictPolicy
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
        val conflictPolicy = ConflictPolicy()
        when (val op = ChangeMaterializer.materialize(change)) {
            is MaterializeOp.UpsertEntity -> {
                val existing = db.entityDao().byId(op.id)
                if (existing == null) {
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
                } else {
                    when (val outcome = conflictPolicy.decide(change, existing.version)) {
                        is ConflictOutcome.Accepted -> {
                            // Real UPDATE (not IGNORE insert) so the version and
                            // incoming fields actually apply to the existing row.
                            db.entityDao().updateFields(
                                id = op.id,
                                type = op.kind,
                                attributesJson = JSONObject(mapOf("title" to op.title)).toString(),
                                status = op.status,
                                version = outcome.nextVersion,
                            )
                        }
                        is ConflictOutcome.Loser -> {
                            android.util.Log.w("PA_SYNC", "conflict: entity ${op.id} not updated (${outcome.reason})")
                        }
                    }
                }
            }
            is MaterializeOp.UpsertInbox -> {
                val existing = db.inboxDao().byId(op.id)
                if (existing == null) {
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
                } else {
                    when (val outcome = conflictPolicy.decide(change, existing.version)) {
                        is ConflictOutcome.Accepted -> {
                            // Real UPDATE (not IGNORE insert) so version and fields
                            // apply; keep local capturedAt/createdAt, bump updatedAt.
                            db.inboxDao().updateFields(
                                id = op.id,
                                kind = op.kind,
                                state = op.state,
                                transcriptId = op.transcriptId,
                                body = op.body,
                                sourceDeviceId = op.sourceDeviceId,
                                updatedAt = op.updatedAt,
                                retentionClass = op.retentionClass,
                                version = outcome.nextVersion,
                            )
                        }
                        is ConflictOutcome.Loser -> {
                            android.util.Log.w("PA_SYNC", "conflict: inbox ${op.id} not updated (${outcome.reason})")
                        }
                    }
                }
            }
            is MaterializeOp.UpsertTranscript -> {
                val existing = db.transcriptDao().forInboxItem(op.inboxItemId)
                    .firstOrNull { it.id == op.id }
                var accepted = false
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
                    accepted = true
                } else {
                    when (val outcome = conflictPolicy.decide(change, existing.version)) {
                        is ConflictOutcome.Accepted -> {
                            db.transcriptDao().updateTextStatusVersion(
                                id = op.id,
                                text = op.text,
                                status = op.status,
                                editedAt = change.occurredAt,
                                version = outcome.nextVersion,
                            )
                            accepted = true
                        }
                        is ConflictOutcome.Loser -> {
                            android.util.Log.w("PA_SYNC", "conflict: transcript ${op.id} not updated (${outcome.reason})")
                        }
                    }
                }
                // A peer-originated transcript must also carry its inbox state
                // transition (e.g. CAPTURED -> TRANSCRIBED), which the sender
                // includes as patch["state"]. Apply it only when the transcript
                // was actually accepted, never on a losing edit.
                if (accepted) {
                    change.patch["state"]?.let { state ->
                        db.inboxDao().updateState(op.inboxItemId, state, change.occurredAt)
                    }
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
        logicalClock = logicalClock,
        idempotencyKey = idempotencyKey,
        retentionClass = retentionClass.name,
        provenanceJson = encodeProvenance(provenance),
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
            logicalClock = logicalClock,
            idempotencyKey = idempotencyKey,
            provenance = decodeProvenance(provenanceJson),
            retentionClass = RetentionClass.valueOf(retentionClass),
        )
    }

    private fun encodeProvenance(entries: List<ru.rudra.androidos.pa.domain.model.ProvenanceEntry>): String {
        val arr = org.json.JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("source", e.source.name)
                    .put("actor", e.actor)
                    .put("at", e.at)
                    // JSONObject.put(k, null) removes the key, so store the
                    // absence marker explicitly to keep null vs "" distinct.
                    .put("detail", e.detail ?: org.json.JSONObject.NULL)
            )
        }
        return arr.toString()
    }

    private fun decodeProvenance(json: String): List<ru.rudra.androidos.pa.domain.model.ProvenanceEntry> {
        if (json.isBlank()) return emptyList()
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ru.rudra.androidos.pa.domain.model.ProvenanceEntry(
                source = ru.rudra.androidos.pa.domain.model.ProvenanceSource.valueOf(o.getString("source")),
                actor = o.getString("actor"),
                at = o.getString("at"),
                detail = if (o.isNull("detail")) null else o.optString("detail"),
            )
        }
    }
}