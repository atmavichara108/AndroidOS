package ru.rudra.androidos.pa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

@Composable
fun InboxScreen(
    state: UiInboxState,
    onAction: (UiInboxAction) -> Unit,
    itemExtra: @Composable (UiInboxItem) -> Unit = {},
) {
    var text by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New note") },
        )
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
    }
}
