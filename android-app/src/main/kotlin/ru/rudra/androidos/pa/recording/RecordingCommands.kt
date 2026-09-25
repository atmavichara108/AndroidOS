package ru.rudra.androidos.pa.recording

import android.content.Context
import android.content.Intent

/**
 * UI-facing command entry point. Sends start/pause/resume/stop to
 * [RecordingService] the same way the widget does (idempotent intent extras).
 */
object RecordingCommands {
    fun send(context: Context, command: String) {
        val intent = Intent(context, RecordingService::class.java)
            .putExtra(RecordingService.KEY_COMMAND, command)
        context.startForegroundService(intent)
    }
}
