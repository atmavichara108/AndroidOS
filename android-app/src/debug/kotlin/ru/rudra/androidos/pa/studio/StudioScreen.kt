@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.rudra.androidos.pa.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rudra.androidos.pa.ui.InboxScreen
import ru.rudra.androidos.pa.ui.UiInboxAction
import ru.rudra.androidos.pa.ui.UiRecording

/**
 * Embedded UI lab. It deliberately has no Android, Room, microphone or alarm side effects.
 * The same presentation components can later be fed by the production ViewModel.
 */
@Composable
fun StudioScreen(
    modifier: Modifier = Modifier,
    initialScenario: StudioScenario = StudioScenario.CAPTURED,
) {
    var state by remember(initialScenario) { mutableStateOf(initialScenario.toUiState()) }
    var actions by remember(initialScenario) { mutableStateOf(emptyList<StudioAction>()) }
    var dark by remember { mutableStateOf(true) }
    var inspector by remember { mutableStateOf(true) }
    fun dispatch(action: StudioAction) {
        state = reduceStudio(state, action)
        actions = actions + action
    }

    MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xffa6ef79),
        background = androidx.compose.ui.graphics.Color(0xff0b160c),
        surface = androidx.compose.ui.graphics.Color(0xff0b160c),
    ) else androidx.compose.material3.lightColorScheme()) {
    Surface(modifier = modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("PIP-BOY / STUDIO", style = MaterialTheme.typography.titleLarge)
                    Text("UI scenario laboratory", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (state.simulated) "SIMULATED" else "LIVE",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            ScenarioPicker(state.scenario) {
                dispatch(StudioAction.SelectScenario(it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dispatch(StudioAction.Reset) }) { Text("Reset fixture") }
                OutlinedButton(onClick = { state = replayStudio(initialScenario, actions) }) { Text("Replay session") }
                OutlinedButton(onClick = { dark = !dark }) { Text(if (dark) "Light theme" else "Pip-Boy theme") }
                OutlinedButton(onClick = { inspector = !inspector }) { Text("Inspector") }
            }
            InboxScreen(state = state.toUiInboxState(), onAction = { uiAction ->
                when (uiAction) {
                    is UiInboxAction.ApproveTask -> dispatch(StudioAction.Approve(uiAction.id, "task"))
                    is UiInboxAction.ApproveEvent -> dispatch(StudioAction.Approve(uiAction.id, "event"))
                    is UiInboxAction.Capture -> Unit
                    is UiInboxAction.Transcribe -> dispatch(StudioAction.Transcribe(uiAction.id))
                    is UiInboxAction.BeginTranscriptEdit -> dispatch(StudioAction.BeginTranscriptEdit(uiAction.id))
                    is UiInboxAction.SaveTranscriptEdit -> dispatch(StudioAction.SaveTranscriptEdit(uiAction.id, uiAction.text))
                    is UiInboxAction.CancelTranscriptEdit -> dispatch(StudioAction.CancelTranscriptEdit(uiAction.id))
                    is UiInboxAction.Delete -> dispatch(StudioAction.Delete(uiAction.id))
                    is UiInboxAction.RequestApprove -> dispatch(StudioAction.RequestApprove(uiAction.id, uiAction.kind))
                    is UiInboxAction.ConfirmApproval -> dispatch(StudioAction.ConfirmApproval(uiAction.id, uiAction.kind))
                    UiInboxAction.CancelApproval -> dispatch(StudioAction.CancelApproval)
                }
            },
                recordingLabel = if (state.error == null) state.recording.label else null,
                onRecord = { dispatch(StudioAction.ToggleRecording) },
                recordings = listOf(
                    UiRecording("/simulated/demo-rec.m4a", "09-26 14:00", 48_512),
                    UiRecording("/simulated/long-rec.m4a", "09-25 09:30", 1_048_576),
                ),
            )
            if (inspector) InspectorPanel(state, actions)
            SyncPanel(state.sync) { action -> dispatch(action) }
            }
        }
    }
    }
}

@Composable
private fun ScenarioPicker(selected: StudioScenario, onSelect: (StudioScenario) -> Unit) {
    Text("SCENARIOS", style = MaterialTheme.typography.labelLarge)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioScenario.values().forEach { scenario ->
            if (scenario == selected) {
                Button(onClick = { onSelect(scenario) }) { Text(scenario.title) }
            } else {
                OutlinedButton(onClick = { onSelect(scenario) }) { Text(scenario.title) }
            }
        }
    }
    Text(selected.description, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InspectorPanel(state: StudioUiState, actions: List<StudioAction>) {
    Text("INSPECTOR", style = MaterialTheme.typography.labelLarge)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("scenario = ${state.scenario.name}", style = MaterialTheme.typography.bodySmall)
            Text("simulated = ${state.simulated}", style = MaterialTheme.typography.bodySmall)
            Text("items = ${state.items.size}", style = MaterialTheme.typography.bodySmall)
            Text("approvals = ${state.approvals.size}; error = ${state.error}", style = MaterialTheme.typography.bodySmall)
            Text("replay = ${actions.joinToString(" > ")}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SyncPanel(sync: StudioSyncState, onAction: (StudioAction) -> Unit) {
    Text("SYNC / CONFLICTS", style = MaterialTheme.typography.labelLarge)
    Text("Status: ${sync.status} · pending: ${sync.pendingChanges}")
    if (sync.conflicts.isEmpty()) {
        OutlinedButton(onClick = { onAction(StudioAction.StartSync) }) { Text("Simulate sync") }
    } else {
        sync.conflicts.forEach { conflict ->
            Text("${conflict.field}: ${conflict.localValue} / ${conflict.remoteValue}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAction(StudioAction.ResolveConflict(conflict.id, false)) }) {
                    Text("Keep local")
                }
                Button(onClick = { onAction(StudioAction.ResolveConflict(conflict.id, true)) }) {
                    Text("Use remote")
                }
            }
        }
    }
}
