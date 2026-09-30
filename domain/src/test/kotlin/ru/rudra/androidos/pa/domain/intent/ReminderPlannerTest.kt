package ru.rudra.androidos.pa.domain.intent

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now: Instant = Instant.parse("2026-09-29T09:00:00Z")

    // 2026-09-29 is a Tuesday. Moscow is UTC+3.
    private fun localDate(triggerAt: String?): LocalDate =
        Instant.parse(triggerAt).atZone(zone).toLocalDate()

    private fun localTime(triggerAt: String?): LocalTime =
        Instant.parse(triggerAt).atZone(zone).toLocalTime()

    @Test
    fun `offset in minutes adds duration`() {
        val d = ReminderPlanner.decide("купить молоко через 30 минут", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(now.plus(Duration.ofMinutes(30)).toString(), d.triggerAt)
    }

    @Test
    fun `offset in hours adds hours`() {
        val d = ReminderPlanner.decide("позвонить через 2 часа", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(now.plus(Duration.ofHours(2)).toString(), d.triggerAt)
    }

    @Test
    fun `clock time sets today at that time`() {
        val d = ReminderPlanner.decide("встреча в 14:30", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertNotNull(d.triggerAt)
        assertEquals(LocalDate.of(2026, 9, 29), localDate(d.triggerAt))
        assertEquals(LocalTime.of(14, 30), localTime(d.triggerAt))
    }

    @Test
    fun `tomorrow at clock time`() {
        val d = ReminderPlanner.decide("завтра в 10:00 отправить отчет", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 9, 30), localDate(d.triggerAt))
        assertEquals(LocalTime.of(10, 0), localTime(d.triggerAt))
    }

    @Test
    fun `word hour sets today hour`() {
        val d = ReminderPlanner.decide("созвон в 15 часов", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 9, 29), localDate(d.triggerAt))
        assertEquals(LocalTime.of(15, 0), localTime(d.triggerAt))
    }

    @Test
    fun `task without time needs no reminder`() {
        val d = ReminderPlanner.decide("купить молоко", IntentKind.TASK, now, zone)
        assertFalse(d.needsReminder)
        assertEquals(null, d.triggerAt)
    }

    @Test
    fun `event without time still wants reminder`() {
        val d = ReminderPlanner.decide("встреча с врачом", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(null, d.triggerAt)
    }

    @Test
    fun `habit is recurring with daily repeat`() {
        val d = ReminderPlanner.decide("заниматься спортом ежедневно в 7 часов", IntentKind.HABIT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals("DAILY", d.repeat)
        assertEquals(LocalTime.of(7, 0), localTime(d.triggerAt))
    }

    @Test
    fun `weekday schedules next occurrence`() {
        // 2026-09-29 is a Tuesday; next Friday is 2026-10-02, at 09:00
        val d = ReminderPlanner.decide("тренировка в пятницу", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 10, 2), localDate(d.triggerAt))
        assertEquals(LocalTime.of(9, 0), localTime(d.triggerAt))
    }

    @Test
    fun `tomorrow alone defaults to noon`() {
        val d = ReminderPlanner.decide("сделать это завтра", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 9, 30), localDate(d.triggerAt))
        assertEquals(LocalTime.of(12, 0), localTime(d.triggerAt))
    }

    @Test
    fun `spoken hour tomorrow sets nine`() {
        val d = ReminderPlanner.decide("встреча завтра в девять", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 9, 30), localDate(d.triggerAt))
        assertEquals(LocalTime.of(9, 0), localTime(d.triggerAt))
    }

    @Test
    fun `spoken hour with evening marker adds twelve`() {
        val d = ReminderPlanner.decide("ужин в шесть вечера", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 9, 29), localDate(d.triggerAt))
        assertEquals(LocalTime.of(18, 0), localTime(d.triggerAt))
    }

    @Test
    fun `spoken hour after weekday still parses the hour`() {
        // 2026-09-29 is a Tuesday; next Friday is 2026-10-02.
        val d = ReminderPlanner.decide("тренировка в пятницу в семь", IntentKind.TASK, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalDate.of(2026, 10, 2), localDate(d.triggerAt))
        assertEquals(LocalTime.of(7, 0), localTime(d.triggerAt))
    }

    @Test
    fun `spoken noon stays twelve`() {
        val d = ReminderPlanner.decide("встреча в полдень", IntentKind.EVENT, now, zone)
        assertTrue(d.needsReminder)
        assertEquals(LocalTime.of(12, 0), localTime(d.triggerAt))
    }
}