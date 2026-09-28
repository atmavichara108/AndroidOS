package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.Change

/**
 * Change-log watermarking and compaction for the laptop peer.
 *
 * Problem: PeerStore appends every change permanently and export re-sends the
 * whole history each time, so repeated exchanges are O(n²) and the log grows
 * without bound. This class is the pure rule for what is still "pending" vs
 * already acknowledged, independent of any store.
 *
 * Rule: a change is pending until its idempotency key appears in the acked set.
 * A change becomes acked when the peer APPLIES a bundle it earlier SENT — i.e.
 * the counterparty echoed our own change back (either as a deduped duplicate or
 * as part of a round-trip), which proves it was durably received. Once acked, a
 * change is eligible for compaction and is excluded from the next export.
 *
 * The acked set only ever grows; revocation/identity are unaffected. Nothing is
 * dropped before it is acked, so no acknowledged data is ever lost to a crash
 * between send and ack.
 */
class ChangeWatermark {

    private val acked = HashSet<String>()

    /** Pending changes: those not yet acknowledged (what export should send). */
    fun pending(all: List<Change>): List<Change> =
        all.filter { it.idempotencyKey !in acked }

    /** Marks changes acknowledged. Returns the number newly acked. */
    fun ack(changes: List<Change>): Int =
        ackKeys(changes.map { it.idempotencyKey }.toSet())

    /** Marks already-known idempotency keys as acknowledged (seed/restore). */
    fun ackKeys(keys: Set<String>): Int {
        var newly = 0
        for (k in keys) {
            if (acked.add(k)) newly++
        }
        return newly
    }

    /** True when nothing remains to send. */
    fun isQuiescent(all: List<Change>): Boolean = pending(all).isEmpty()

    fun ackedCount(): Int = acked.size

    /**
     * Compacts a list into (pending, acked-eligible) by removing acked entries.
     * Returns the pending list; acked entries are meant to be moved to a
     * separate compacted store (their provenance is preserved there).
     */
    fun compact(all: List<Change>): Pair<List<Change>, List<Change>> {
        val pending = all.filter { it.idempotencyKey !in acked }
        val acknowledged = all.filter { it.idempotencyKey in acked }
        return pending to acknowledged
    }
}