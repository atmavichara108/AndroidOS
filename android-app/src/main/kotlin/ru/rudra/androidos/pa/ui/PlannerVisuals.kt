package ru.rudra.androidos.pa.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared visual helpers for the planner surfaces (task board, Today). Pure
 * presentation, no state — kept here so the board and the Today view render a
 * priority or a deadline the same way.
 */

/** Small colored tag for a task priority (HIGH/MEDIUM/LOW); renders nothing when absent or unknown. */
@Composable
fun PriorityTag(priority: String?) {
    val normalized = priority?.uppercase() ?: return
    val (background, label) = when (normalized) {
        "HIGH" -> MaterialTheme.colorScheme.errorContainer to "Высокий"
        "MEDIUM" -> MaterialTheme.colorScheme.tertiaryContainer to "Средний"
        "LOW" -> MaterialTheme.colorScheme.surfaceVariant to "Низкий"
        else -> return
    }
    Surface(color = background, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
