package ru.rudra.androidos.pa.data

import org.json.JSONObject
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.port.LocalStore

class RoomLocalStore(private val db: PaDatabase) : LocalStore {

    override fun applyChange(change: Change): Boolean {
        if (db.changeDao().exists(change.id, change.idempotencyKey)) {
            return false
        }
        db.changeDao().insert(change.toRow())
        return true
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
}
