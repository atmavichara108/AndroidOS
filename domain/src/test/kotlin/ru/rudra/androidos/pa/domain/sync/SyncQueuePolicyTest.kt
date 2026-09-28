package ru.rudra.androidos.pa.domain.sync

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.rudra.androidos.pa.domain.sync.AttemptDecision.ALLOWED
import ru.rudra.androidos.pa.domain.sync.AttemptDecision.IN_COOLDOWN
import ru.rudra.androidos.pa.domain.sync.AttemptDecision.NO_PENDING
import ru.rudra.androidos.pa.domain.sync.AttemptDecision.TRANSPORT_BLOCKED
import ru.rudra.androidos.pa.domain.sync.SyncQueuePolicy.shouldAttempt
import ru.rudra.androidos.pa.domain.sync.SyncQueuePolicy.backoffDelay

class SyncQueuePolicyTest {

    private val cooldown = Duration.ofMinutes(5)

    @Test
    fun `no pending changes means no attempt`() {
        assertEquals(NO_PENDING, shouldAttempt(false, TransportAvailability.AVAILABLE, null, cooldown))
        assertEquals(NO_PENDING, shouldAttempt(false, TransportAvailability.BLOCKED, null, cooldown))
    }

    @Test
    fun `blocked transport prevents attempt even with pending`() {
        assertEquals(
            TRANSPORT_BLOCKED,
            shouldAttempt(true, TransportAvailability.BLOCKED, null, cooldown),
        )
    }

    @Test
    fun `available transport with no prior attempt allows immediately`() {
        assertEquals(ALLOWED, shouldAttempt(true, TransportAvailability.AVAILABLE, null, cooldown))
    }

    @Test
    fun `respects cooldown between attempts`() {
        assertEquals(IN_COOLDOWN, shouldAttempt(true, TransportAvailability.AVAILABLE, Duration.ofMinutes(1), cooldown))
        assertEquals(ALLOWED, shouldAttempt(true, TransportAvailability.AVAILABLE, Duration.ofMinutes(5), cooldown))
        assertEquals(ALLOWED, shouldAttempt(true, TransportAvailability.AVAILABLE, Duration.ofMinutes(6), cooldown))
    }

    @Test
    fun `backoff grows exponentially and is capped`() {
        val base = Duration.ofSeconds(10)
        val max = Duration.ofMinutes(2)
        assertEquals(Duration.ZERO, backoffDelay(0, base, max))
        assertEquals(Duration.ofSeconds(10), backoffDelay(1, base, max))
        assertEquals(Duration.ofSeconds(20), backoffDelay(2, base, max))
        assertEquals(Duration.ofSeconds(40), backoffDelay(3, base, max))
        assertEquals(max, backoffDelay(10, base, max), "capped at max")
    }

    @Test
    fun `negative failure count treated as zero`() {
        assertEquals(Duration.ZERO, backoffDelay(-3, Duration.ofSeconds(10), Duration.ofMinutes(2)))
    }
}