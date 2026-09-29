package ru.rudra.androidos.pa.domain.retention

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.RetentionClass

class RetentionPolicyTest {

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val policy = RetentionPolicy()

    @Test
    fun `permanent never expires`() {
        val since = Instant.parse("2020-01-01T00:00:00Z")
        assertFalse(policy.isExpired(RetentionClass.PERMANENT, since, now))
        assertEquals(null, policy.expiredAt(RetentionClass.PERMANENT, since))
    }

    @Test
    fun `temporary audio expires after ttl`() {
        val since = now.minus(Duration.ofDays(6))
        assertFalse(policy.isExpired(RetentionClass.TEMPORARY_AUDIO, since, now))
        val old = now.minus(Duration.ofDays(8))
        assertTrue(policy.isExpired(RetentionClass.TEMPORARY_AUDIO, old, now))
    }

    @Test
    fun `temporary transcript has longer ttl than audio`() {
        // 8 days: audio expired, transcript still valid
        val since = now.minus(Duration.ofDays(8))
        assertTrue(policy.isExpired(RetentionClass.TEMPORARY_AUDIO, since, now))
        assertFalse(policy.isExpired(RetentionClass.TEMPORARY_TRANSCRIPT, since, now))
    }

    @Test
    fun `session expires after 24h`() {
        val since = now.minus(Duration.ofHours(23))
        assertFalse(policy.isExpired(RetentionClass.SESSION, since, now))
        val stale = now.minus(Duration.ofHours(25))
        assertTrue(policy.isExpired(RetentionClass.SESSION, stale, now))
    }

    @Test
    fun `purgeCandidates only returns expired non-deleted records`() {
        val records = listOf(
            RetentionCandidate("a", RetentionClass.PERMANENT, now.minus(Duration.ofDays(365))),
            RetentionCandidate("b", RetentionClass.TEMPORARY_AUDIO, now.minus(Duration.ofDays(8))),
            RetentionCandidate("c", RetentionClass.TEMPORARY_TRANSCRIPT, now.minus(Duration.ofDays(60))),
            RetentionCandidate("d", RetentionClass.TEMPORARY_AUDIO, now.minus(Duration.ofDays(8)), deletedAt = now),
        )
        val candidates = policy.purgeCandidates(records, now)
        assertEquals(listOf("b", "c"), candidates.map { it.id })
    }

    @Test
    fun `custom ttl map is honoured`() {
        val short = RetentionPolicy(mapOf(RetentionClass.TEMPORARY_TRANSCRIPT to Duration.ofMinutes(1)))
        assertTrue(short.isExpired(RetentionClass.TEMPORARY_TRANSCRIPT, now.minus(Duration.ofMinutes(2)), now))
        assertFalse(short.isExpired(RetentionClass.TEMPORARY_TRANSCRIPT, now, now))
    }

    @Test
    fun `parseClass maps known labels and nulls unknown ones`() {
        assertEquals(RetentionClass.PERMANENT, policy.parseClass("PERMANENT"))
        assertEquals(RetentionClass.TEMPORARY_AUDIO, policy.parseClass("TEMPORARY_AUDIO"))
        assertEquals(RetentionClass.TEMPORARY_TRANSCRIPT, policy.parseClass("TEMPORARY_TRANSCRIPT"))
        assertEquals(RetentionClass.SESSION, policy.parseClass("SESSION"))
        assertEquals(null, policy.parseClass("GARBAGE"))
        assertEquals(null, policy.parseClass(""))
        assertEquals(null, policy.parseClass(null))
    }
}