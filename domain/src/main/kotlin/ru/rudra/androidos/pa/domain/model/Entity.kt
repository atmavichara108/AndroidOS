package ru.rudra.androidos.pa.domain.model

data class Entity(
    val id: String,
    val type: EntityType,
    val schemaVersion: Int,
    val attributes: Map<String, String>,
    val provenance: List<ProvenanceEntry>,
    val status: EntityStatus,
    val version: Long,
    val deletedAt: String? = null,
)

data class EntityType(val namespace: String, val name: String)

enum class EntityStatus { PROPOSED, APPROVED, REJECTED }

sealed interface TypedEntityValue

data class EventAttributes(
    val title: String,
    val startsAt: String,
    val endsAt: String?,
    val location: String?,
) : TypedEntityValue

data class TaskAttributes(
    val title: String,
    val dueAt: String?,
    val priority: String?,
) : TypedEntityValue

data class ContactAttributes(
    val displayName: String,
    val identifiers: Map<String, String>,
) : TypedEntityValue
