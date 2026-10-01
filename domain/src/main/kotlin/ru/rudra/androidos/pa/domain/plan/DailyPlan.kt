package ru.rudra.androidos.pa.domain.plan

import java.time.LocalDate

/**
 * Daily plan classification (P2-02 daily plan). A pure function over tasks:
 * given each task's due date and the reference "today", group into overdue /
 * today / upcoming / no-due. The caller converts an entity's stored dueAt to a
 * [LocalDate] (e.g. via Instant + the user's zone) so this domain logic stays
 * deterministic and unit-testable with no storage or UI dependency.
 */
data class DailyPlanItem(
    val id: String,
    val title: String,
    val dueAt: LocalDate?,
    val priority: String? = null,
    val projectId: String? = null,
    val project: String? = null,
)

enum class DueStatus { OVERDUE, TODAY, UPCOMING, NO_DUE }

data class DailyPlan(
    val overdue: List<DailyPlanItem>,
    val today: List<DailyPlanItem>,
    val upcoming: List<DailyPlanItem>,
    val noDue: List<DailyPlanItem>,
) {
    val isEmpty: Boolean
        get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && noDue.isEmpty()

    val total: Int
        get() = overdue.size + today.size + upcoming.size + noDue.size
}

object DailyPlanner {

    fun status(item: DailyPlanItem, today: LocalDate): DueStatus = when {
        item.dueAt == null -> DueStatus.NO_DUE
        item.dueAt < today -> DueStatus.OVERDUE
        item.dueAt == today -> DueStatus.TODAY
        else -> DueStatus.UPCOMING
    }

    fun plan(items: List<DailyPlanItem>, today: LocalDate): DailyPlan {
        val grouped = items.groupBy { status(it, today) }
        return DailyPlan(
            overdue = grouped[DueStatus.OVERDUE] ?: emptyList(),
            today = grouped[DueStatus.TODAY] ?: emptyList(),
            upcoming = grouped[DueStatus.UPCOMING] ?: emptyList(),
            noDue = grouped[DueStatus.NO_DUE] ?: emptyList(),
        )
    }

    fun byStatus(plan: DailyPlan, status: DueStatus): List<DailyPlanItem> = when (status) {
        DueStatus.OVERDUE -> plan.overdue
        DueStatus.TODAY -> plan.today
        DueStatus.UPCOMING -> plan.upcoming
        DueStatus.NO_DUE -> plan.noDue
    }
}