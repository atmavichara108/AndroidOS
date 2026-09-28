package ru.rudra.androidos.pa.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class UiTaskCard(
    val id: String,
    val title: String,
    val project: String? = null,
    val dueLabel: String? = null,
    val priority: String? = null,
)

data class UiTaskColumn(
    val id: String,
    val title: String,
    val cards: List<UiTaskCard> = emptyList(),
)

data class UiTaskBoardState(
    val title: String = "Board",
    val columns: List<UiTaskColumn> = emptyList(),
)

sealed interface UiTaskBoardAction {
    data class MoveTask(val taskId: String, val targetColumnId: String) : UiTaskBoardAction
}

@Composable
fun TaskBoardScreen(
    state: UiTaskBoardState,
    onAction: (UiTaskBoardAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.title, style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.columns.forEach { column ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(column.title, style = MaterialTheme.typography.titleMedium)
                        if (column.cards.isEmpty()) Text("No tasks", style = MaterialTheme.typography.bodySmall)
                        column.cards.forEach { task ->
                            TaskCard(task)
                            state.columns.filter { it.id != column.id }.take(2).forEach { target ->
                                OutlinedButton(onClick = { onAction(UiTaskBoardAction.MoveTask(task.id, target.id)) }) {
                                    Text("→ ${target.title}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: UiTaskCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            task.project?.let { Text("Project: $it", style = MaterialTheme.typography.bodySmall) }
            task.dueLabel?.let { Text("Due: $it", style = MaterialTheme.typography.bodySmall) }
            task.priority?.let { Text("Priority: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
