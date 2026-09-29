package ru.rudra.androidos.pa.domain.calendar

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarEventPlannerTest {

    @Test
    fun `single event gets default duration`() {
        val event = CalendarEventPlanner.single("Созвон", "2026-09-29T12:00:00Z")
        assertEquals("Созвон", event.title)
        assertEquals("2026-09-29T12:00:00Z", event.startsAt)
        assertEquals("2026-09-29T12:30:00Z", event.endsAt)
        assertNull(event.location)
    }

    @Test
    fun `default duration is thirty minutes`() {
        assertEquals(Duration.ofMinutes(30), CalendarEventPlanner.defaultDuration)
    }

    @Test
    fun `location is carried through`() {
        val event = CalendarEventPlanner.single("Встреча", "2026-09-29T09:00:00Z", "Офис")
        assertEquals("Офис", event.location)
    }

    @Test
    fun `end follows start across a day boundary`() {
        val event = CalendarEventPlanner.single("Поздний созвон", "2026-09-29T23:50:00Z")
        assertEquals("2026-09-30T00:20:00Z", event.endsAt)
    }

    @Test
    fun `instant is preserved as iso string`() {
        val minutes = CalendarEventPlanner.single("X", "2026-01-02T03:04:05Z")
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toString(), minutes.startsAt)
    }
}