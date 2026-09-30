package ru.rudra.androidos.pa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.rudra.androidos.pa.domain.plan.DailyPlan
import ru.rudra.androidos.pa.domain.plan.DailyPlanItem
import java.time.LocalDate

class DailyPlanMapperTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test fun bucketsKeepDomainOrderAndCarryItems() {
        val plan = DailyPlan(
            overdue = listOf(DailyPlanItem("a", "old", today.minusDays(2), "High")),
            today = listOf(DailyPlanItem("b", "now", today)),
            upcoming = listOf(DailyPlanItem("c", "later", today.plusDays(3))),
            noDue = listOf(DailyPlanItem("d", "someday", null)),
        )

        val state = dailyPlanToUiState(plan)

        assertEquals(listOf("OVERDUE", "TODAY", "UPCOMING", "NO_DUE"), state.buckets.map { it.id })
        assertEquals(listOf("a"), state.buckets[0].items.map { it.id })
        assertEquals(listOf("b"), state.buckets[1].items.map { it.id })
        assertEquals(listOf("c"), state.buckets[2].items.map { it.id })
        assertEquals(listOf("d"), state.buckets[3].items.map { it.id })
        assertEquals("High", state.buckets[0].items.single().priority)
        assertEquals(4, state.total)
        assertFalse(state.isEmpty)
    }

    @Test fun dueFormatIsAppliedAndMissingDueStaysNull() {
        val plan = DailyPlan(
            overdue = emptyList(),
            today = listOf(DailyPlanItem("b", "now", today)),
            upcoming = emptyList(),
            noDue = listOf(DailyPlanItem("d", "someday", null)),
        )

        val state = dailyPlanToUiState(
            plan = plan,
            title = "Today",
            subtitle = "Sep 29",
            dueFormat = { it.dayOfMonth.toString() },
        )

        assertEquals("Today", state.title)
        assertEquals("Sep 29", state.subtitle)
        assertEquals("29", state.buckets[1].items.single().dueLabel)
        assertEquals(null, state.buckets[3].items.single().dueLabel)
    }

    @Test fun emptyPlanReportsEmpty() {
        val state = dailyPlanToUiState(DailyPlan(emptyList(), emptyList(), emptyList(), emptyList()))

        assertEquals(4, state.buckets.size)
        assertEquals(0, state.total)
        assertTrue(state.isEmpty)
    }
}