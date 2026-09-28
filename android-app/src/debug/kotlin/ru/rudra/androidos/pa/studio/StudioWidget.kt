package ru.rudra.androidos.pa.studio

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Debug-only one-tap launcher for StudioActivity. Not a second Studio: the
 * widget only opens the embedded UI lab so testing needs no adb command.
 */
class StudioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { StudioWidgetBody() }
    }
}

@Composable
private fun StudioWidgetBody() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
            .cornerRadius(12.dp)
            .clickable(actionRunCallback<OpenStudioAction>(actionParametersOf()))
            .padding(8.dp),
    ) {
        Text(
            text = "PIP-BOY / STUDIO",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFFB300)),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
        )
        Text(
            text = "OPEN UI LAB",
            style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 11.sp),
        )
    }
}

class OpenStudioAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent().setComponent(
            android.content.ComponentName(
                context.packageName,
                "ru.rudra.androidos.pa.studio.StudioActivity",
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

class StudioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudioWidget()
}
