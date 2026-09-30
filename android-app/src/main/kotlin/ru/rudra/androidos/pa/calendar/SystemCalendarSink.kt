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

    /**
     * Human-readable name of the calendar an approval would write to, or null
     * when the app cannot reach one (permission not granted, no writable
     * calendar). Callers show this next to the reminder time so the user sees
     * where the event lands before confirming; it reads only, so it is safe to
     * call while rendering an approval preview and never writes anything.
     */
    fun previewLabel(): String? {
        if (!hasReadPermission()) return null
        val calendar = writableCalendar()
        return calendar?.name
    }

    override fun write(event: CalendarEvent): CalendarWriteResult {
        if (!hasWritePermission()) {
            return CalendarWriteResult.Unavailable("WRITE_CALENDAR not granted")
        }
        val calendar = writableCalendar()
            ?: return CalendarWriteResult.Unavailable("no writable calendar on device")

        return try {
            val startMillis = Instant.parse(event.startsAt).toEpochMilli()
            val endMillis = event.endsAt?.let { Instant.parse(it).toEpochMilli() }
                ?: (startMillis + DEFAULT_DURATION_MILLIS)

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendar.id)
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

    private fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private data class WritableCalendar(val id: Long, val name: String)

    /**
     * A calendar this app may write to: the local (account-less) one when the
     * device has it, otherwise the first calendar with contributor rights or
     * better. Deliberately not ordered by IS_PRIMARY — that column is not
     * exposed by every provider (it is not on this device), and an unsupported
     * sort column fails the whole query, which would report "no writable
     * calendar" on a phone that has several.
     */
    private fun writableCalendar(): WritableCalendar? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                var fallback: WritableCalendar? = null
                while (cursor.moveToNext()) {
                    val candidate = WritableCalendar(
                        id = cursor.getLong(0),
                        name = cursor.getString(2) ?: "календарь",
                    )
                    if (cursor.getString(1) == CalendarContract.ACCOUNT_TYPE_LOCAL) return@use candidate
                    if (fallback == null) fallback = candidate
                }
                fallback
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "PA_CALENDAR"
        const val DEFAULT_DURATION_MILLIS = 30L * 60L * 1000L
        const val REMINDER_MINUTES_BEFORE = 10
    }
}