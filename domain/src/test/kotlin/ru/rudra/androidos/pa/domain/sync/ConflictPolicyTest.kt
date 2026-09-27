package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass

class ConflictPolicyTest {

    private val policy = ConflictPolicy()

    private fun change(
        baseVersion: Long?,
        id: String = "c-$baseVersion",
        occurredAt: String = "2026-09-26T10:00:00Z",
    ) = Change(
        id = id,
        entityId = "e-1",
        operation = ChangeOperation.UPDATE,
        patch = mapOf("title" to "t"),
        actorDeviceId = "phone-1",
        baseVersion = baseVersion,
        occurredAt = occurredAt,
        logicalClock = null,
        idempotencyKey = "k-$id",
        provenance = emptyList(),
        retentionClass = RetentionClass.PERMANENT,
    )

    @Test
    fun `no version tracking accepts and advances to 1`() {
        assertEquals(ConflictOutcome.Accepted(1L), policy.decide(change(null), null))
    }

    @Test
    fun `matching base advances by one`() {
        assertEquals(ConflictOutcome.Accepted(2L), policy.decide(change(1L), 1L))
    }

    @Test
    fun `no base applies to any current version`() {
        assertEquals(ConflictOutcome.Accepted(5L), policy.decide(change(null), 4L))
    }

    @Test
    fun `stale base loses`() {
        val outcome = policy.decide(change(1L), 3L)
        assertTrue(outcome is ConflictOutcome.Loser)
        assertEquals("stale base 1 vs current 3", outcome.reason)
    }

    @Test
    fun `ahead base catches up`() {
        // The sender is further along than we are; accept and jump to it.
        assertEquals(ConflictOutcome.Accepted(5L), policy.decide(change(4L), 2L))
    }

    @Test
    fun `create on missing entity is accepted`() {
        // currentVersion null + CREATE -> the materializer path will insert v1.
        assertEquals(ConflictOutcome.Accepted(1L), policy.decide(change(null), null))
    }
}