package ru.rudra.androidos.pa.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import ru.rudra.androidos.pa.domain.statemachine.CaptureCommand
import ru.rudra.androidos.pa.domain.statemachine.CaptureStateMachine
import ru.rudra.androidos.pa.domain.statemachine.CaptureState
import java.io.File
import java.util.UUID

class RecordingService : Service() {

    private val stateMachine = CaptureStateMachine()
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var currentSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra(KEY_COMMAND)
        when (command) {
            "start" -> onStart()
            "pause" -> onPause()
            "resume" -> onResume()
            "stop" -> onStop()
        }
        return START_STICKY
    }

    private fun onStart() {
        val result = stateMachine.dispatch(CaptureCommand.Start)
        if (result.changed) {
            startForegroundCompat()
            currentSessionId = UUID.randomUUID().toString()
            currentFile = newRecordingFile(currentSessionId!!)
            recorder = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }
        }
    }

    private fun onPause() {
        val result = stateMachine.dispatch(CaptureCommand.Pause)
        if (result.changed) {
            pauseRecorder()
        }
    }

    private fun onResume() {
        val result = stateMachine.dispatch(CaptureCommand.Resume)
        if (result.changed) {
            resumeRecorder()
        }
    }

    private fun onStop() {
        val result = stateMachine.dispatch(CaptureCommand.Stop)
        if (result.changed) {
            stopRecorder()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.pause()
        }
    }

    private fun resumeRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.resume()
        }
    }

    private fun stopRecorder() {
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    private fun newRecordingFile(sessionId: String): File {
        val dir = if (Build.VERSION.SDK_INT >= 31) {
            getExternalFilesDir(Environment.DIRECTORY_RECORDINGS) ?: filesDir
        } else {
            filesDir
        }
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$sessionId.m4a")
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
        val notification: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AndroidOS PA")
                .setContentText("Recording")
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_COMMAND = "command"
        private const val CHANNEL_ID = "pa_recording"
        private const val NOTIFICATION_ID = 42
    }
}
