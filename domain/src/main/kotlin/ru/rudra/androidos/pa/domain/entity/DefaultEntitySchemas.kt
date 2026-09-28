package ru.rudra.androidos.pa.domain.entity

import ru.rudra.androidos.pa.domain.model.EntityType

/**
 * The standard PA entity types (P2-02). Registered once at startup; new types
 * join the system by adding a schema here (or via [EntityRegistry.register])
 * without any storage migration.
 */
object DefaultEntitySchemas {

    fun all(): List<EntitySchema> = listOf(
        EntitySchema(EntityType("pa", "TASK"), required = setOf("title"), optional = setOf("dueAt", "priority", "projectId", "status")),
        EntitySchema(EntityType("pa", "EVENT"), required = setOf("title", "startsAt"), optional = setOf("endsAt", "location")),
        EntitySchema(EntityType("pa", "CONTACT"), required = setOf("displayName"), optional = setOf("phone", "email", "org")),
        EntitySchema(EntityType("pa", "PROJECT"), required = setOf("title"), optional = setOf("description", "status", "parentProjectId")),
        EntitySchema(EntityType("pa", "MEETING"), required = setOf("title", "startsAt"), optional = setOf("endsAt", "attendees", "location")),
        EntitySchema(EntityType("pa", "PROMISE"), required = setOf("title"), optional = setOf("toWhom", "deadline", "status")),
        EntitySchema(EntityType("pa", "IDEA"), required = setOf("title"), optional = setOf("note", "tags")),
        EntitySchema(EntityType("pa", "NOTE"), required = setOf("text"), optional = setOf("tags", "link")),
        EntitySchema(EntityType("pa", "HABIT"), required = setOf("title"), optional = setOf("frequency", "status")),
        EntitySchema(EntityType("pa", "GOAL"), required = setOf("title"), optional = setOf("target", "deadline", "status")),
        EntitySchema(EntityType("pa", "RELATION"), required = setOf("kind", "otherId"), optional = setOf("label")),
    )
}