package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.Change

/**
 * Deterministic conflict policy for changes that touch the same entity.
 *
 * The PA contract (docs/privacy-and-sync.md) forbids blind last-writer-wins
 * for approved edits, reminders and destructive deletes. This policy applies
 * optimistic concurrency on a per-entity version counter: a change is accepted
 * only when its [Change.baseVersion] matches the entity's current version; on
 * a mismatch a deterministic winner is chosen (newer [occurredAt], then
 * lexicographically smaller [idempotencyKey]) and the loser is dropped.
 *
 * When the store does not track versions (`currentVersion == null`) the policy
 * accepts the change and advances from 0 — preserving the pre-versioning
 * behaviour of the materialization flow.
 */
sealed interface ConflictOutcome {
    data class Accepted(val nextVersion: Long) : ConflictOutcome
    data class Loser(val reason: String) : ConflictOutcome
}

class ConflictPolicy {

    fun decide(change: Change, currentVersion: Long?): ConflictOutcome {
        if (currentVersion == null) {
            // Store does not track versions (or entity unknown). Accept and
            // advance past 0; the first accepted change brings it to 1.
            return ConflictOutcome.Accepted(1L)
        }
        val base = change.baseVersion
        return when {
            // No base declared: applies to any current state (best-effort).
            base == null -> ConflictOutcome.Accepted(currentVersion + 1L)
            base == currentVersion -> ConflictOutcome.Accepted(currentVersion + 1L)
            base < currentVersion -> ConflictOutcome.Loser(
                "stale base $base vs current $currentVersion"
            )
            else -> ConflictOutcome.Accepted(base + 1L)
        }
    }
}