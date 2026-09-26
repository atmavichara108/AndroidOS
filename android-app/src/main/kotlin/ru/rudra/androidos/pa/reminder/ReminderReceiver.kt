package ru.rudra.androidos.pa.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import ru.rudra.androidos.pa.MainActivity
import ru.rudra.androidos.pa.R
import ru.rudra.androidos.pa.data.PaDatabase
import ru.rudra.androidos.pa.domain.model.ReminderState

/**
 * Fires when a reminder alarm elapses: shows a notification with the title of
 * the target entity and marks the reminder DONE. DB access happens on a
 * background thread (Room forbids main-thread IO).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val targetId = intent.getStringExtra(ReminderScheduler.EXTRA_TARGET_ID) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val db = PaDatabase.get(appContext)
                val entity = db.entityDao().byId(targetId)
                val title = entity?.attributesJson
                    ?.let { runCatching { JSONObject(it).optString("title") }.getOrNull() }
                    ?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.reminder_fallback_title)
                val type = entity?.type?.lowercase() ?: "reminder"

                notify(appContext, reminderId, "Напоминание ($type)", title)
                db.reminderDao().setState(reminderId, ReminderState.DONE.name)
            } catch (e: Exception) {
                android.util.Log.e("PA_REMINDER", "failed to fire reminder $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun notify(context: Context, reminderId: String, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
        val tapIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        nm.notify(reminderId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "pa_reminders"
    }
}
