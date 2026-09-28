package ru.rudra.androidos.pa.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.EntityType

class DefaultEntitySchemasTest {

    @Test
    fun `all standard PA types are registered and validate`() {
        val registry = EntityRegistry(DefaultEntitySchemas.all())
        assertEquals(11, registry.types().size)
        // Every schema validates its own valid attributes.
        for (schema in DefaultEntitySchemas.all()) {
            val validAttrs = buildMap {
                schema.required.forEach { put(it, "тест-значение") }
            }
            assertTrue(
                registry.isValid(schema.type, validAttrs),
                "schema for ${schema.type.namespace}.${schema.type.name} should validate its required fields"
            )
        }
    }

    @Test
    fun `TASK requires title but tolerates optional fields`() {
        val registry = EntityRegistry(DefaultEntitySchemas.all())
        val task = EntityType("pa", "TASK")
        assertTrue(registry.isValid(task, mapOf("title" to "позвонить", "priority" to "high", "dueAt" to "2026-10-01")))
        assertTrue(!registry.isValid(task, mapOf("priority" to "high")))
    }

    @Test
    fun `EVENT requires title and startsAt`() {
        val registry = EntityRegistry(DefaultEntitySchemas.all())
        val event = EntityType("pa", "EVENT")
        assertTrue(registry.isValid(event, mapOf("title" to "встреча", "startsAt" to "2026-10-01T10:00:00Z")))
        assertTrue(!registry.isValid(event, mapOf("title" to "встреча")))
    }

    @Test
    fun `unknown type is not registered`() {
        val registry = EntityRegistry(DefaultEntitySchemas.all())
        assertTrue(!registry.isRegistered(EntityType("pa", "HOVERBOARD")))
    }
}