package ru.rudra.androidos.pa.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules and cancels reminder alarms. Reminders are persisted as
 * ReminderRow; this object turns a row into a real system alarm so the
 * notification actually fires.
 */
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_TARGET_ID = "targetId"

    fun schedule(context: Context, reminderId: String, targetId: String, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, reminderId, targetId)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // [проверить] без разрешения на точные алармы система сдвигает время;
            // для P1 срез принимаем дрейф, пользователю предлагается грант позже.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, reminderId, ""))
    }

    private fun pendingIntent(context: Context, reminderId: String, targetId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .putExtra(EXTRA_TARGET_ID, targetId)
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
