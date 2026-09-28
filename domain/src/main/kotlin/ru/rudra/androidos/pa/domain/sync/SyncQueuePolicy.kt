package ru.rudra.androidos.pa.domain.sync

import java.time.Duration

/**
 * Scheduling rule for "user-controlled delayed transport" (docs/architecture.md):
 * the app does not push continuously; it attempts to send pending changes only
 * when transport is available AND enough time has passed since the last attempt,
 * and it backs off on failure so it never hammers an unreachable peer. Pure and
 * testable — no store or transport dependency.
 */
enum class TransportAvailability { AVAILABLE, BLOCKED }

enum class AttemptDecision { ALLOWED, NO_PENDING, TRANSPORT_BLOCKED, IN_COOLDOWN }

object SyncQueuePolicy {

    /**
     * Whether a sync attempt should run now.
     *
     * @param hasPending whether the change queue has unsent changes
     * @param transport availability of the current transport
     * @param sinceLastAttempt elapsed time since the previous attempt (null if none)
     * @param cooldown minimum interval between attempts
     */
    fun shouldAttempt(
        hasPending: Boolean,
        transport: TransportAvailability,
        sinceLastAttempt: Duration?,
        cooldown: Duration,
    ): AttemptDecision = when {
        !hasPending -> AttemptDecision.NO_PENDING
        transport == TransportAvailability.BLOCKED -> AttemptDecision.TRANSPORT_BLOCKED
        sinceLastAttempt != null && sinceLastAttempt < cooldown -> AttemptDecision.IN_COOLDOWN
        else -> AttemptDecision.ALLOWED
    }

    /**
     * Exponential backoff before the next retry after [failureCount] consecutive
     * failures, capped at [max]. attempt = 1 -> base, attempt = 2 -> 2*base, ...
     */
    fun backoffDelay(failureCount: Int, base: Duration, max: Duration): Duration {
        if (failureCount <= 0) return Duration.ZERO
        val factor = 1L shl (failureCount - 1)
        val candidate = base.multipliedBy(factor)
        return if (candidate.compareTo(max) > 0) max else candidate
    }
}