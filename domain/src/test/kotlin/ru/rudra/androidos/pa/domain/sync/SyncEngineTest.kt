package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass

class SyncEngineTest {

    private val engine = SyncEngine("phone-1")

    private fun change(
        id: String,
        key: String = "key-$id",
        occurredAt: String = "2026-09-26T10:00:00Z",
        op: ChangeOperation = ChangeOperation.CREATE,
        entityId: String = "entity-$id",
    ) = Change(
        id = id,
        entityId = entityId,
        operation = op,
        patch = mapOf("title" to "t-$id"),
        actorDeviceId = "phone-1",
        baseVersion = null,
        occurredAt = occurredAt,
        logicalClock = null,
        idempotencyKey = key,
        provenance = emptyList(),
        retentionClass = RetentionClass.PERMANENT,
    )

    private class FakeStore : ru.rudra.androidos.pa.domain.port.LocalStore {
        val applied = LinkedHashSet<String>()
        override fun applyChange(change: Change): Boolean =
            applied.add(change.idempotencyKey)
    }

    @Test
    fun `build orders changes deterministically and sets hash`() {
        val env = engine.buildEnvelope(
            sequence = 1,
            changes = listOf(
                change("b", occurredAt = "2026-09-26T10:00:02Z"),
                change("a", occurredAt = "2026-09-26T10:00:01Z"),
            ),
            createdAt = "2026-09-26T10:01:00Z",
        )
        assertEquals(listOf("a", "b"), env.changes.map { it.id })
        assertTrue(env.bundleHash.isNotBlank())
    }

    @Test
    fun `same changes always produce the same hash`() {
        val changes = listOf(change("b"), change("a"))
        val e1 = engine.buildEnvelope(1, changes, createdAt = "T")
        val e2 = engine.buildEnvelope(2, changes.reversed(), createdAt = "T")
        assertEquals(e1.bundleHash, e2.bundleHash)
    }

    @Test
    fun `apply is idempotent across duplicate envelopes`() {
        val store = FakeStore()
        val env = engine.buildEnvelope(1, listOf(change("a"), change("b")), createdAt = "T")
        val first = engine.apply(env, store)
        val second = engine.apply(env, store)
        assertEquals(2, first.applied)
        assertEquals(0, first.duplicates)
        assertEquals(0, second.applied)
        assertEquals(2, second.duplicates)
        assertEquals(2, store.applied.size)
    }

    @Test
    fun `duplicate changes inside one envelope are applied once`() {
        val store = FakeStore()
        val same = change("a")
        val env = engine.buildEnvelope(1, listOf(same, same), createdAt = "T")
        val report = engine.apply(env, store)
        assertEquals(1, report.applied)
        assertEquals(1, report.duplicates)
        assertEquals(1, store.applied.size)
    }

    @Test
    fun `reordered envelopes converge to the same state`() {
        val storeA = FakeStore()
        val storeB = FakeStore()
        val c1 = change("a", occurredAt = "2026-09-26T10:00:01Z")
        val c2 = change("b", occurredAt = "2026-09-26T10:00:02Z")
        val e1 = engine.buildEnvelope(1, listOf(c1), createdAt = "T")
        val e2 = engine.buildEnvelope(2, listOf(c2), createdAt = "T")

        engine.apply(e1, storeA); engine.apply(e2, storeA)
        engine.apply(e2, storeB); engine.apply(e1, storeB)

        assertEquals(storeA.applied.toSet(), storeB.applied.toSet())
        assertEquals(setOf("key-a", "key-b"), storeA.applied.toSet())
    }

    @Test
    fun `tampered envelope is rejected whole`() {
        val store = FakeStore()
        val env = engine.buildEnvelope(1, listOf(change("a")), createdAt = "T")
        val tampered = env.copy(changes = env.changes + change("evil"))
        val report = engine.apply(tampered, store)
        assertTrue(report.rejected)
        assertEquals("bundleHash mismatch", report.reason)
        assertTrue(store.applied.isEmpty())
    }

    @Test
    fun `tombstones are hashed and detectable`() {
        val store = FakeStore()
        val env = engine.buildEnvelope(
            sequence = 1,
            changes = listOf(change("a", op = ChangeOperation.TOMBSTONE)),
            tombstones = listOf("entity-a"),
            createdAt = "T",
        )
        val tampered = env.copy(tombstones = listOf("entity-a", "entity-x"))
        assertTrue(engine.apply(tampered, store).rejected)
        val ok = engine.apply(env, store)
        assertFalse(ok.rejected)
        assertEquals(1, ok.applied)
    }

    @Test
    fun `expired envelope is rejected`() {
        val store = FakeStore()
        val env = engine.buildEnvelope(
            sequence = 1,
            changes = listOf(change("a")),
            createdAt = "2020-01-01T00:00:00Z",
            expiresAt = "2020-01-02T00:00:00Z",
        )
        val report = engine.apply(env, store)
        assertTrue(report.rejected)
        assertEquals("expired", report.reason)
    }
}
