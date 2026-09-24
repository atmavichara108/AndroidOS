package ru.rudra.androidos.pa.domain.model

data class Change(
    val id: String,
    val entityId: String,
    val operation: ChangeOperation,
    val patch: Map<String, String>,
    val actorDeviceId: String,
    val baseVersion: Long?,
    val occurredAt: String,
    val logicalClock: String?,
    val idempotencyKey: String,
    val provenance: List<ProvenanceEntry>,
    val retentionClass: RetentionClass,
)

enum class ChangeOperation { CREATE, UPDATE, DELETE, TOMBSTONE }
