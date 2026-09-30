package ru.rudra.androidos.pa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class UiPlanItem(
    val id: String,
    val title: String,
    val dueLabel: String? = null,
    val priority: String? = null,
)

data class UiPlanBucket(
    val id: String,
    val title: String,
    val items: List<UiPlanItem> = emptyList(),
)

data class UiDailyPlanState(
    val title: String = "Today",
    val subtitle: String? = null,
    val buckets: List<UiPlanBucket> = emptyList(),
) {
    val total: Int
        get() = buckets.sumOf { it.items.size }

    val isEmpty: Boolean
        get() = total == 0
}

sealed interface UiDailyPlanAction {
    data class CompleteTask(val id: String) : UiDailyPlanAction
}

/**
 * "Today" projection of the planner: the same tasks the board shows, grouped by
 * due status (overdue / today / upcoming / no due). Pure presentation — the host
 * supplies already-classified buckets, so no date logic or state lives here.
 */
@Composable
fun DailyPlanScreen(
    state: UiDailyPlanState,
    onAction: (UiDailyPlanAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            state.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.isEmpty) {
            Text("Nothing planned", style = MaterialTheme.typography.bodySmall)
        } else {
            state.buckets.filterNot { it.items.isEmpty() }.forEach { bucket ->
                PlanBucket(bucket, onAction)
            }
        }
    }
}

@Composable
private fun PlanBucket(bucket: UiPlanBucket, onAction: (UiDailyPlanAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${bucket.title} (${bucket.items.size})", style = MaterialTheme.typography.titleMedium)
        bucket.items.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        item.dueLabel?.let { Text("Due: $it", style = MaterialTheme.typography.bodySmall) }
                        item.priority?.let { Text("Priority: $it", style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = { onAction(UiDailyPlanAction.CompleteTask(item.id)) }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}