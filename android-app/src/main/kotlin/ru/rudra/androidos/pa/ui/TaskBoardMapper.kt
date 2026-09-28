package ru.rudra.androidos.pa.ui

import ru.rudra.androidos.pa.domain.model.Entity

/** Maps approved domain entities into the presentation board without persistence concerns. */
fun approvedEntitiesToTaskBoard(
    entities: List<Entity>,
    title: String = "Tasks",
): UiTaskBoardState {
    val columns = linkedMapOf(
        "BACKLOG" to "Backlog",
        "READY" to "Ready",
        "IN_PROGRESS" to "In progress",
        "BLOCKED" to "Blocked",
        "DONE" to "Done",
        "CANCELLED" to "Cancelled",
    ).map { (id, label) -> UiTaskColumn(id, label) }.toMutableList()

    entities
        .asSequence()
        .filter { it.status.name == "APPROVED" && it.type.name == "TASK" }
        .map { entity ->
            val attrs = entity.attributes
            val status = attrs["status"]?.uppercase() ?: "BACKLOG"
            status to UiTaskCard(
                id = entity.id,
                title = attrs["title"] ?: "Untitled task",
                project = attrs["projectId"] ?: attrs["project"],
                dueLabel = attrs["dueAt"],
                priority = attrs["priority"],
            )
        }
        .forEach { (status, card) ->
            val index = columns.indexOfFirst { it.id == status }.let { if (it >= 0) it else 0 }
            columns[index] = columns[index].copy(cards = columns[index].cards + card)
        }

    return UiTaskBoardState(title = title, columns = columns)
}
