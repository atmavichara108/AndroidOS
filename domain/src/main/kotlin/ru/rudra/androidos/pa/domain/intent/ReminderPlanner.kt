package ru.rudra.androidos.pa.domain.intent

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Decides whether a classified intent needs a reminder/notification and, if so,
 * at what absolute trigger time (docs/roadmap.md: personal productivity
 * contour). Rules-first temporal parsing of the common spoken cues:
 *
 * - "через N минут/час/часов/день/дней" -> now + duration
 * - "в HH:MM" / "в H часов / H час"     -> today at that time (or tomorrow if past)
 * - "завтра" / "завтра в HH:MM"         -> tomorrow
 * - weekday names                       -> next occurrence
 *
 * Pure and deterministic over [now] and [zone]; when no cue is detected the
 * decision reports no reminder, so the host can ask via the clarification
 * path rather than guess a time.
 */
data class ReminderDecision(
    val needsReminder: Boolean,
    val triggerAt: String? = null,
    val repeat: String? = null,
)

object ReminderPlanner {

    private val clockTime: Regex = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val wordHour: Regex = Regex("""\bв (\d{1,2}) (час|часа|часов)\b""")
    private val offset: Regex = Regex("""\bчерез (\d{1,4}) (минут|минуту|час|часа|часов|день|дня|дней|недел\w*)\b""")

    private val weekdayNames: Map<String, Int> = mapOf(
        "понедельник" to 1, "вторник" to 2, "среда" to 3, "среду" to 3,
        "четверг" to 4, "пятница" to 5, "пятницу" to 5, "суббота" to 6, "субботу" to 6,
        "воскресенье" to 7,
    )

    fun decide(text: String, intentKind: IntentKind, now: Instant, zone: ZoneId): ReminderDecision {
        val lower = text.lowercase()
        val today = now.atZone(zone).toLocalDate()

        // Time-bound kinds always want a reminder; the trigger may still be null
        // (host asks) if no time cue is found.
        val alwaysWantsReminder = intentKind == IntentKind.EVENT || intentKind == IntentKind.MEETING

        val trigger = parseTime(lower, now, zone, today)
        if (trigger != null) {
            val repeat = if (intentKind == IntentKind.HABIT) "DAILY" else null
            return ReminderDecision(true, trigger, repeat)
        }

        if (alwaysWantsReminder) {
            return ReminderDecision(true)
        }

        return ReminderDecision(false)
    }

    private fun parseTime(lower: String, now: Instant, zone: ZoneId, today: LocalDate): String? {
        val offsetMatch = offset.find(lower)
        if (offsetMatch != null) {
            val amount = offsetMatch.groupValues[1].toLong()
            val unit = offsetMatch.groupValues[2]
            val minutes = when {
                unit.startsWith("минут") -> amount
                unit.startsWith("час") -> amount * 60
                unit.startsWith("день") || unit.startsWith("дн") -> amount * 24 * 60
                unit.startsWith("недел") -> amount * 7 * 24 * 60
                else -> return null
            }
            return now.plus(Duration.ofMinutes(minutes)).toString()
        }

        val tomorrow = lower.contains("завтра")
        val clock = clockTime.find(lower)
        if (clock != null) {
            val hour = clock.groupValues[1].toInt()
            val minute = clock.groupValues[2].toInt()
            val day = if (tomorrow) today.plusDays(1) else today
            return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toString()
        }

        val wordHourMatch = wordHour.find(lower)
        if (wordHourMatch != null) {
            val hour = wordHourMatch.groupValues[1].toInt()
            val day = if (tomorrow) today.plusDays(1) else today
            return day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toString()
        }

        if (tomorrow) {
            return today.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant().toString()
        }

        weekdayNames.forEach { (word, iso) ->
            if (lower.contains(word)) {
                return nextWeekday(today, iso).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toString()
            }
        }

        return null
    }

    private fun nextWeekday(today: LocalDate, isoWeekday: Int): LocalDate {
        // strictly next occurrence, so "в пятницу" on a Friday means next Friday
        var candidate = today.plusDays(1)
        while (candidate.dayOfWeek.value != isoWeekday) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}