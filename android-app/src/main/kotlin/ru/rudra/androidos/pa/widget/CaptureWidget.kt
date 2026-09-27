package ru.rudra.androidos.pa.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.sp
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.Spacer
import androidx.glance.layout.width

class CaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetBody()
        }
    }
}

@Composable
private fun WidgetBody() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
            .padding(8.dp)
            .cornerRadius(12.dp)
    ) {
        Text(
            text = "PIP-BOY / CAPTURE",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFFB300)),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = "VOICE INPUT",
            style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 11.sp),
            modifier = GlanceModifier.padding(bottom = 6.dp),
        )
        Row {
            WButton("Record", "start")
            WButton("Pause", "pause")
        }
        Row(modifier = GlanceModifier.padding(top = 4.dp)) {
            WButton("Resume", "resume")
            WButton("Stop", "stop")
        }
    }
}

@Composable
private fun WButton(label: String, command: String) {
    Text(
        text = label,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(Color(0xFFEAEAEA)),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        ),
        modifier = GlanceModifier
            .padding(start = 2.dp, end = 2.dp)
            .background(Color(0xFF33323A))
            .cornerRadius(6.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(
                actionRunCallback<WidgetCommandAction>(
                    actionParametersOf(WidgetCommandAction.COMMAND to command)
                )
            ),
    )
}

class WidgetCommandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[COMMAND] ?: return
        val intent = android.content.Intent(context, ru.rudra.androidos.pa.recording.RecordingService::class.java)
            .putExtra(CaptureWidgetReceiver.KEY_COMMAND, command)
        context.startForegroundService(intent)
    }

    companion object {
        val COMMAND = ActionParameters.Key<String>("command")
    }
}
