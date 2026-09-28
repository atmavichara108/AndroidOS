package ru.rudra.androidos.pa.domain.plan

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyPlanTest {

    private val today = LocalDate.of(2026, 9, 29)
    private fun item(id: String, due: LocalDate?, title: String = "t$id", prio: String? = null) =
        DailyPlanItem(id = id, title = title, dueAt = due, priority = prio)

    @Test
    fun `classifies overdue today upcoming and no-due`() {
        val items = listOf(
            item("1", today.minusDays(2)),
            item("2", today),
            item("3", today.plusDays(5)),
            item("4", null),
        )
        val plan = DailyPlanner.plan(items, today)
        assertEquals(listOf("1"), plan.overdue.map { it.id })
        assertEquals(listOf("2"), plan.today.map { it.id })
        assertEquals(listOf("3"), plan.upcoming.map { it.id })
        assertEquals(listOf("4"), plan.noDue.map { it.id })
        assertEquals(4, plan.total)
    }

    @Test
    fun `due yesterday is overdue due tomorrow is upcoming`() {
        val items = listOf(
            item("a", today.minusDays(1)),
            item("b", today.plusDays(1)),
        )
        val plan = DailyPlanner.plan(items, today)
        assertTrue(plan.overdue.map { it.id } == listOf("a"))
        assertTrue(plan.upcoming.map { it.id } == listOf("b"))
    }

    @Test
    fun `empty input yields empty plan`() {
        val plan = DailyPlanner.plan(emptyList(), today)
        assertTrue(plan.isEmpty)
        assertEquals(0, plan.total)
    }

    @Test
    fun `byStatus returns the right bucket`() {
        val plan = DailyPlanner.plan(
            listOf(item("o", today.minusDays(1)), item("t", today), item("n", null)),
            today,
        )
        assertEquals(listOf("o"), DailyPlanner.byStatus(plan, DueStatus.OVERDUE).map { it.id })
        assertEquals(listOf("t"), DailyPlanner.byStatus(plan, DueStatus.TODAY).map { it.id })
        assertEquals(emptyList<String>(), DailyPlanner.byStatus(plan, DueStatus.UPCOMING).map { it.id })
        assertEquals(listOf("n"), DailyPlanner.byStatus(plan, DueStatus.NO_DUE).map { it.id })
    }

    @Test
    fun `no-due items never count as today`() {
        val items = listOf(item("x", null))
        assertFalse(DailyPlanner.plan(items, today).today.isNotEmpty())
        assertEquals(listOf("x"), DailyPlanner.plan(items, today).noDue.map { it.id })
    }
}