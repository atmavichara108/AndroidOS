package ru.rudra.androidos.pa.domain.retention

import java.time.Duration
import java.time.Instant
import ru.rudra.androidos.pa.domain.model.RetentionClass

/**
 * Data-level retention policy (docs/privacy-and-sync.md): raw audio and
 * raw/superseded transcripts have configurable retention and must be removed
 * together with their blobs, FTS/index entries, caches and propagated deletion
 * tombstones. Each record carries a [RetentionClass]; this policy decides when
 * a record of a given class is expired and due for purge. Pure and testable —
 * no storage or UI dependency.
 *
 * TTLs are configurable; defaults: PERMANENT never expires, SESSION ~24h,
 * TEMPORARY_AUDIO ~7d (bulky raw audio), TEMPORARY_TRANSCRIPT ~30d.
 */
class RetentionPolicy(
    private val ttls: Map<RetentionClass, Duration> = defaultTtls(),
) {

    /** When a record captured/referenced at [since] of [retentionClass] expires, or null if never. */
    fun expiredAt(retentionClass: RetentionClass, since: Instant): Instant? {
        val ttl = ttls[retentionClass] ?: return null
        return since.plus(ttl)
    }

    /** Whether a record of [retentionClass] captured at [since] is expired as of [now]. */
    fun isExpired(retentionClass: RetentionClass, since: Instant, now: Instant): Boolean {
        val expiry = expiredAt(retentionClass, since) ?: return false
        return !now.isBefore(expiry)
    }

    /** Records due for purge as of [now]: expired and not already deleted. */
    fun purgeCandidates(records: List<RetentionCandidate>, now: Instant): List<RetentionCandidate> =
        records.filter { it.deletedAt == null && isExpired(it.retentionClass, it.since, now) }

    /**
     * Safe label-to-class parser: unknown/malformed retention labels resolve to
     * null rather than throwing, so a corrupt row cannot break a whole purge.
     * Callers should treat a null result as never-expiring (fail-safe).
     */
    fun parseClass(label: String?): RetentionClass? = runCatching {
        label?.let(RetentionClass::valueOf)
    }.getOrNull()

    companion object {
        fun defaultTtls(): Map<RetentionClass, Duration> = mapOf(
            RetentionClass.SESSION to Duration.ofHours(24),
            RetentionClass.TEMPORARY_AUDIO to Duration.ofDays(7),
            RetentionClass.TEMPORARY_TRANSCRIPT to Duration.ofDays(30),
        )
    }
}

/** A record the retention policy evaluates for purge. */
data class RetentionCandidate(
    val id: String,
    val retentionClass: RetentionClass,
    val since: Instant,
    val deletedAt: Instant? = null,
)