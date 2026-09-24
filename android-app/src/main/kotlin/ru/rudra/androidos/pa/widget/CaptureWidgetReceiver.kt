package ru.rudra.androidos.pa.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import ru.rudra.androidos.pa.recording.RecordingService

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(KEY_COMMAND)
        if (command != null) {
            val svc = Intent(context, RecordingService::class.java)
                .putExtra(RecordingService.KEY_COMMAND, command)
            context.startForegroundService(svc)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val KEY_COMMAND = "command"
    }
}
