package ru.rudra.androidos.pa.domain.entity

import ru.rudra.androidos.pa.domain.model.EntityType

/**
 * Declarative description of one entity type. Registering a type here is how a
 * new entity kind joins the system without touching storage schema or
 * migrating every existing row (per docs/architecture.md: "Новые Entity.type
 * регистрируются через schema/validation/UI registry без migration каждой
 * сущности").
 *
 * [required] and [optional] name the allowed attribute keys; unknown keys are
 * rejected, so a typo in an attribute key cannot silently persist.
 */
data class EntitySchema(
    val type: EntityType,
    val required: Set<String> = emptySet(),
    val optional: Set<String> = emptySet(),
) {
    val attributeNames: Set<String> get() = required + optional

    fun validate(attributes: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        for (key in required) {
            if (attributes[key].isNullOrBlank()) {
                problems += "missing required attribute '$key' for ${type.namespace}.${type.name}"
            }
        }
        for (key in attributes.keys) {
            if (key !in attributeNames) {
                problems += "unknown attribute '$key' for ${type.namespace}.${type.name}"
            }
        }
        return problems
    }
}

/** Holds the set of registered entity schemas and validates entities against them. */
class EntityRegistry(initial: Collection<EntitySchema> = emptyList()) {

    private val byType = LinkedHashMap<EntityType, EntitySchema>()

    init {
        initial.forEach { register(it) }
    }

    /** Registers (or replaces) a schema. Returns the previous schema, if any. */
    fun register(schema: EntitySchema): EntitySchema? = byType.put(schema.type, schema)

    fun schemaOf(type: EntityType): EntitySchema? = byType[type]

    fun isRegistered(type: EntityType): Boolean = byType.containsKey(type)

    /** All registered types, in registration order. */
    fun types(): List<EntityType> = byType.keys.toList()

    fun validate(type: EntityType, attributes: Map<String, String>): List<String> {
        val schema = byType[type]
            ?: return listOf("unregistered entity type ${type.namespace}.${type.name} — register a schema first")
        return schema.validate(attributes)
    }

    fun isValid(type: EntityType, attributes: Map<String, String>): Boolean =
        validate(type, attributes).isEmpty()
}