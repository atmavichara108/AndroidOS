package ru.rudra.androidos.pa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class UiInboxItem(
    val id: String,
    val body: String,
    val stateLabel: String,
    val capturedLabel: String,
)

data class UiInboxState(
    val items: List<UiInboxItem> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = false,
)

sealed interface UiInboxAction {
    data class ApproveTask(val id: String) : UiInboxAction
    data class ApproveEvent(val id: String) : UiInboxAction
    data class Capture(val text: String) : UiInboxAction
}

/** Clean UI record of a finished recording; hosts map their store type into it. */
data class UiRecording(
    val path: String,
    val label: String,
    val sizeBytes: Long,
)

@Composable
fun InboxScreen(
    state: UiInboxState,
    onAction: (UiInboxAction) -> Unit,
    itemExtra: @Composable (UiInboxItem) -> Unit = {},
    recordingLabel: String? = null,
    onRecord: (() -> Unit)? = null,
    recordings: List<UiRecording> = emptyList(),
    onPlay: ((UiRecording) -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    val hasRecord = recordingLabel != null && onRecord != null

    Column(Modifier.padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New note") },
                modifier = Modifier.weight(1f),
            )
            if (hasRecord) {
                OutlinedButton(
                    onClick = { onRecord?.invoke() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(recordingLabel)
                }
            }
        }
        Button(
            onClick = {
                val t = text
                if (t.isNotBlank()) {
                    text = ""
                    onAction(UiInboxAction.Capture(t))
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Capture")
        }
        if (state.isLoading) {
            Text("Loading…", Modifier.padding(top = 8.dp))
        }
        state.error?.let { err ->
            Text(err, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        if (!state.isLoading && state.error == null && state.items.isEmpty()) {
            Text("No items yet", Modifier.padding(top = 8.dp))
        }
        state.items.forEach { item ->
            Row(Modifier.padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${item.capturedLabel} ${item.body}")
                    itemExtra(item)
                }
                TextButton({ onAction(UiInboxAction.ApproveTask(item.id)) }) { Text("→Task") }
                TextButton({ onAction(UiInboxAction.ApproveEvent(item.id)) }) { Text("→Event") }
            }
        }
        if (recordings.isNotEmpty()) {
            Text("Recordings", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            recordings.forEach { rec ->
                TextButton(
                    onClick = { onPlay?.invoke(rec) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = onPlay != null,
                ) {
                    val kb = rec.sizeBytes / 1024
                    Text("${rec.label} · ${kb} KB", Modifier.fillMaxWidth(1f))
                }
            }
        }
    }
}
