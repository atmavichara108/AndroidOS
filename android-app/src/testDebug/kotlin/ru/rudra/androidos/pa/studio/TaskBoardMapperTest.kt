package ru.rudra.androidos.pa.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.rudra.androidos.pa.domain.model.Entity
import ru.rudra.androidos.pa.domain.model.EntityStatus
import ru.rudra.androidos.pa.domain.model.EntityType
import ru.rudra.androidos.pa.ui.approvedEntitiesToTaskBoard

class TaskBoardMapperTest {
    @Test fun approvedTasksMapToColumnsAndIgnoreOtherEntities() {
        val approvedTask = Entity(
            id = "task-1",
            type = EntityType("pa", "TASK"),
            schemaVersion = 1,
            attributes = mapOf(
                "title" to "Prepare release",
                "status" to "IN_PROGRESS",
                "projectId" to "AndroidOS",
                "dueAt" to "Friday",
                "priority" to "HIGH",
            ),
            provenance = emptyList(),
            status = EntityStatus.APPROVED,
            version = 1,
        )
        val rejected = approvedTask.copy(id = "rejected", status = EntityStatus.REJECTED)
        val event = approvedTask.copy(id = "event-1", type = EntityType("pa", "EVENT"))

        val board = approvedEntitiesToTaskBoard(listOf(approvedTask, rejected, event))
        val progress = board.columns.first { it.id == "IN_PROGRESS" }
        assertEquals(1, progress.cards.size)
        assertEquals("Prepare release", progress.cards.single().title)
        assertEquals("AndroidOS", progress.cards.single().project)
        assertTrue(board.columns.none { column -> column.cards.any { it.id == "rejected" || it.id == "event-1" } })
    }

    @Test fun taskProjectIdResolvesToProjectTitleWhenProjectEntityPresent() {
        val project = Entity(
            id = "proj-1",
            type = EntityType("pa", "PROJECT"),
            schemaVersion = 1,
            attributes = mapOf("title" to "Ремонт"),
            provenance = emptyList(),
            status = EntityStatus.APPROVED,
            version = 1,
        )
        val task = Entity(
            id = "task-2",
            type = EntityType("pa", "TASK"),
            schemaVersion = 1,
            attributes = mapOf(
                "title" to "Купить краску",
                "status" to "TODO",
                "projectId" to "proj-1",
            ),
            provenance = emptyList(),
            status = EntityStatus.APPROVED,
            version = 1,
        )

        val board = approvedEntitiesToTaskBoard(listOf(project, task))
        val todo = board.columns.first { it.id == "TODO" }
        assertEquals("Ремонт", todo.cards.single { it.id == "task-2" }.project)
    }
}
