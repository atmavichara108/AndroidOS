package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass

class ChangeWatermarkTest {

    private fun change(id: String) = Change(
        id = "c-$id",
        entityId = "e-$id",
        operation = ChangeOperation.CREATE,
        patch = mapOf("title" to "t-$id"),
        actorDeviceId = "laptop-peer",
        baseVersion = null,
        occurredAt = "2026-09-28T10:00:00Z",
        logicalClock = null,
        idempotencyKey = "key-$id",
        provenance = emptyList(),
        retentionClass = RetentionClass.PERMANENT,
    )

    @Test
    fun `nothing acked means everything pending`() {
        val wm = ChangeWatermark()
        val all = listOf(change("a"), change("b"))
        assertEquals(all.map { it.idempotencyKey }, wm.pending(all).map { it.idempotencyKey })
        assertTrue(!wm.isQuiescent(all))
    }

    @Test
    fun `acked changes are excluded from pending`() {
        val wm = ChangeWatermark()
        val all = listOf(change("a"), change("b"))
        wm.ack(listOf(change("a")))
        assertEquals(listOf("key-b"), wm.pending(all).map { it.idempotencyKey })
    }

    @Test
    fun `acking the same change twice counts once`() {
        val wm = ChangeWatermark()
        assertEquals(1, wm.ack(listOf(change("a"))))
        assertEquals(0, wm.ack(listOf(change("a"))))
        assertEquals(1, wm.ackedCount())
    }

    @Test
    fun `all acked is quiescent`() {
        val wm = ChangeWatermark()
        val all = listOf(change("a"), change("b"))
        wm.ack(all)
        assertTrue(wm.pending(all).isEmpty())
        assertTrue(wm.isQuiescent(all))
    }

    @Test
    fun `compact splits pending and acknowledged`() {
        val wm = ChangeWatermark()
        val all = listOf(change("a"), change("b"), change("c"))
        wm.ack(listOf(change("b")))
        val (pending, acknowledged) = wm.compact(all)
        assertEquals(listOf("key-a", "key-c"), pending.map { it.idempotencyKey })
        assertEquals(listOf("key-b"), acknowledged.map { it.idempotencyKey })
    }

    @Test
    fun `ack on echo from counterparty marks delivered`() {
        val wm = ChangeWatermark()
        val mine = change("x")
        // I send mine; the counterparty later echoes it back in a bundle I apply.
        val newly = wm.ack(listOf(mine))
        assertEquals(1, newly)
        assertTrue(wm.pending(listOf(mine)).isEmpty())
    }
}