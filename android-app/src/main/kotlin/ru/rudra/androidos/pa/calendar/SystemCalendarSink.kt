package ru.rudra.androidos.pa.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import ru.rudra.androidos.pa.domain.calendar.CalendarEvent
import ru.rudra.androidos.pa.domain.port.CalendarSink
import ru.rudra.androidos.pa.domain.port.CalendarWriteResult
import java.time.Instant

/**
 * [CalendarSink] backed by the phone's system calendar provider
 * ([CalendarContract]). Approving a time-scoped capture writes a real event
 * into the user's own calendar, so it shows up in the stock calendar app and
 * its home-screen widget, and adds a reminder on it.
 *
 * The sink never throws at the caller: a missing permission or an absent
 * writable calendar reports [CalendarWriteResult.Unavailable], so approval of
 * a task still succeeds when the calendar cannot be reached.
 */
class SystemCalendarSink(private val context: Context) : CalendarSink {

    override fun engineId(): String = "system-calendar"

    override fun write(event: CalendarEvent): CalendarWriteResult {
        if (!hasWritePermission()) {
            return CalendarWriteResult.Unavailable("WRITE_CALENDAR not granted")
        }
        val calendarId = writableCalendarId()
            ?: return CalendarWriteResult.Unavailable("no writable calendar on device")

        return try {
            val startMillis = Instant.parse(event.startsAt).toEpochMilli()
            val endMillis = event.endsAt?.let { Instant.parse(it).toEpochMilli() }
                ?: (startMillis + DEFAULT_DURATION_MILLIS)

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return CalendarWriteResult.Error("calendar provider rejected the event")

            val eventId = ContentUris.parseId(uri).toString()
            addReminder(eventId)
            Log.i(TAG, "wrote event $eventId '${event.title}' at ${event.startsAt}")
            CalendarWriteResult.Ok(eventId)
        } catch (e: SecurityException) {
            Log.w(TAG, "calendar write denied", e)
            CalendarWriteResult.Unavailable("calendar permission revoked: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "calendar write failed", e)
            CalendarWriteResult.Error(e.message ?: "unknown calendar error")
        }
    }

    private fun addReminder(eventId: String) {
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId.toLong())
            put(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES_BEFORE)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
            .onFailure { Log.w(TAG, "could not attach reminder to event $eventId", it) }
    }

    private fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** First writable calendar: the account the user's own calendar app shows. */
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "PA_CALENDAR"
        const val DEFAULT_DURATION_MILLIS = 30L * 60L * 1000L
        const val REMINDER_MINUTES_BEFORE = 10
    }
}