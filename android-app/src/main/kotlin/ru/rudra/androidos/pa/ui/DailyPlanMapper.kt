package ru.rudra.androidos.pa.ui

import ru.rudra.androidos.pa.domain.plan.DailyPlan
import ru.rudra.androidos.pa.domain.plan.DailyPlanItem
import java.time.LocalDate

/**
 * Maps a domain [DailyPlan] into the presentation Today view without persistence
 * concerns. The domain already decided the bucket for every item, so this only
 * rewrites due dates through [dueFormat] and keeps the order the planner produced.
 */
fun dailyPlanToUiState(
    plan: DailyPlan,
    title: String = "Today",
    subtitle: String? = null,
    dueFormat: (LocalDate) -> String = { it.toString() },
): UiDailyPlanState {
    fun toItems(source: List<DailyPlanItem>): List<UiPlanItem> = source.map { item ->
        UiPlanItem(
            id = item.id,
            title = item.title,
            dueLabel = item.dueAt?.let(dueFormat),
            priority = item.priority,
        )
    }

    return UiDailyPlanState(
        title = title,
        subtitle = subtitle,
        buckets = listOf(
            UiPlanBucket("OVERDUE", "Overdue", toItems(plan.overdue)),
            UiPlanBucket("TODAY", "Today", toItems(plan.today)),
            UiPlanBucket("UPCOMING", "Upcoming", toItems(plan.upcoming)),
            UiPlanBucket("NO_DUE", "No due date", toItems(plan.noDue)),
        ),
    )
}