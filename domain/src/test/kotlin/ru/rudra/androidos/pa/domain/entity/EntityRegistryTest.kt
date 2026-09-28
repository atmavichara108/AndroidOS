package ru.rudra.androidos.pa.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.EntityType

class EntityRegistryTest {

    private val taskType = EntityType("pa", "TASK")
    private val taskSchema = EntitySchema(
        type = taskType,
        required = setOf("title"),
        optional = setOf("dueAt", "priority", "projectId"),
    )

    @Test
    fun `registered schema validates valid attributes`() {
        val reg = EntityRegistry(listOf(taskSchema))
        assertTrue(reg.isValid(taskType, mapOf("title" to "позвонить", "priority" to "high")))
    }

    @Test
    fun `unregistered type is rejected`() {
        val reg = EntityRegistry(emptyList())
        val problems = reg.validate(taskType, mapOf("title" to "x"))
        assertEquals(1, problems.size)
        assertTrue(problems.first().contains("unregistered"))
    }

    @Test
    fun `missing required attribute is flagged`() {
        val reg = EntityRegistry(listOf(taskSchema))
        assertFalse(reg.isValid(taskType, mapOf("dueAt" to "2026-10-01")))
    }

    @Test
    fun `unknown attribute key is rejected`() {
        val reg = EntityRegistry(listOf(taskSchema))
        val problems = reg.validate(taskType, mapOf("title" to "x", "typoAtt" to "1"))
        assertEquals(1, problems.size)
        assertTrue(problems.first().contains("unknown attribute 'typoAtt'"))
    }

    @Test
    fun `registering a new type is additive without migration`() {
        val reg = EntityRegistry(listOf(taskSchema))
        val projectType = EntityType("pa", "PROJECT")
        reg.register(EntitySchema(projectType, required = setOf("name")))
        assertTrue(reg.isRegistered(projectType))
        assertTrue(reg.isValid(projectType, mapOf("name" to "AndroidOS")))
        assertTrue(reg.isRegistered(taskType), "existing type untouched by new registration")
        assertEquals(2, reg.types().size)
    }

    @Test
    fun `schemaOf returns the registered schema`() {
        val reg = EntityRegistry(listOf(taskSchema))
        assertEquals(taskSchema, reg.schemaOf(taskType))
        assertEquals(null, reg.schemaOf(EntityType("pa", "NOPE")))
    }
}