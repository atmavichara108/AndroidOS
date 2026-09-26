package ru.rudra.androidos.pa.data

import org.json.JSONObject
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.port.LocalStore

class RoomLocalStore(private val db: PaDatabase) : LocalStore {

    override fun applyChange(change: Change): Boolean {
        if (db.changeDao().exists(change.id, change.idempotencyKey)) {
            return false
        }
        db.changeDao().insert(change.toRow())
        return true
    }

    /** All local changes in canonical order — the input for envelope export. */
    fun allChanges(): List<Change> = db.changeDao().all().map { it.toChange() }

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
