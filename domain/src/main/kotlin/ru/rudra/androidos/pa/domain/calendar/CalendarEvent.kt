package ru.rudra.androidos.pa.domain.calendar

import java.time.Duration
import java.time.Instant

/**
 * A time-scoped entity projected for a calendar (docs/roadmap.md: personal
 * productivity contour). Kept separate from the storage [Entity] model so the
 * domain can describe what should be on someone's calendar without knowing
 * whether the sink is the phone's system calendar, a file or a peer.
 *
 * [startsAt]/[endsAt] are ISO-8601 instants; [endsAt] may be absent when the
 * capture named only a moment.
 */
data class CalendarEvent(
    val title: String,
    val startsAt: String,
    val endsAt: String? = null,
    val location: String? = null,
    val description: String? = null,
)

object CalendarEventPlanner {

    /** How long a calendar entry runs when the capture named only its start. */
    val defaultDuration: Duration = Duration.ofMinutes(30)

    /**
     * Builds the calendar projection for a time-scoped capture. An entry that
     * starts at [triggerAt] and has no explicit end runs for
     * [defaultDuration], so the phone's calendar shows a sane block rather than
     * an instantaneous point.
     */
    fun single(title: String, triggerAt: String, location: String? = null): CalendarEvent {
        val start = Instant.parse(triggerAt)
        return CalendarEvent(
            title = title,
            startsAt = start.toString(),
            endsAt = start.plus(defaultDuration).toString(),
            location = location,
        )
    }
}