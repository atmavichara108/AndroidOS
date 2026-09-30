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
    private val wordHour: Regex = Regex("""\bв (\d{1,2})( час| часа| часов)?\b""")
    // Spoken hour, e.g. "в девять", "в шесть вечера". The "часов" word is optional.
    private val wordHourText: Regex = Regex("""\bв ([а-яё]+)( час| часа| часов)?( утра| дня| вечера| ночи)?\b""")
    private val offset: Regex = Regex("""\bчерез (\d{1,4}) (минут|минуту|час|часа|часов|день|дня|дней|недел\w*)\b""")

    // Spoken hour words -> 24h hour. "двенадцать"/"двенадцать часов" stays 12,
    // "полдень" -> 12, "полночь" -> 0. Digit forms are handled by [wordHour].
    private val hourWords: Map<String, Int> = mapOf(
        "один" to 1, "одного" to 1, "одном" to 1,
        "два" to 2, "двух" to 2,
        "три" to 3, "трёх" to 3, "трех" to 3,
        "четыре" to 4, "четырёх" to 4, "четырех" to 4,
        "пять" to 5, "пяти" to 5,
        "шесть" to 6, "шести" to 6,
        "семь" to 7, "семи" to 7,
        "восемь" to 8, "восьми" to 8,
        "девять" to 9, "девяти" to 9,
        "десять" to 10, "десяти" to 10,
        "одиннадцать" to 11, "одиннадцати" to 11,
        "двенадцать" to 12, "двенадцати" to 12,
        "полдень" to 12, "полночь" to 0,
    )

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
            val day = resolveDay(lower, today)
            return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toString()
        }

        val wordHourMatch = wordHour.find(lower)
        if (wordHourMatch != null) {
            val hour = wordHourMatch.groupValues[1].toInt()
            val day = resolveDay(lower, today)
            return day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toString()
        }

        wordHourText.findAll(lower).forEach { match ->
            val spoken = match.groupValues[1]
            val base = hourWords[spoken]
            if (base != null) {
                var hour = base
                val period = match.groupValues[3].trim()
                val pm = period == "вечера" || period == "ночи"
                if (pm && hour < 12) hour += 12
                if (period == "утра" && hour == 12) hour = 0
                val day = resolveDay(lower, today)
                return day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toString()
            }
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

    /**
     * Resolves the day a time cue lands on. "завтра" wins, else the first named
     * weekday in the text, else today. Lets "в пятницу в семь" and
     * "завтра в девять" both attach the parsed hour to the right day.
     */
    private fun resolveDay(lower: String, today: LocalDate): LocalDate {
        if (lower.contains("завтра")) return today.plusDays(1)
        for ((word, iso) in weekdayNames) {
            if (lower.contains(word)) return nextWeekday(today, iso)
        }
        return today
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