package ru.rudra.androidos.pa.studio

import ru.rudra.androidos.pa.ui.UiDailyPlanState
import ru.rudra.androidos.pa.ui.UiInboxItem
import ru.rudra.androidos.pa.ui.PendingApproval
import ru.rudra.androidos.pa.ui.UiInboxState
import ru.rudra.androidos.pa.ui.UiPlanBucket
import ru.rudra.androidos.pa.ui.UiPlanItem
import ru.rudra.androidos.pa.ui.UiTaskBoardAction
import ru.rudra.androidos.pa.ui.UiTaskBoardState
import ru.rudra.androidos.pa.ui.UiTaskCard
import ru.rudra.androidos.pa.ui.UiTaskColumn

/** Synthetic, side-effect-free data used by the embedded UI laboratory. */
enum class StudioScenario(val title: String, val description: String) {
    EMPTY("Empty inbox", "No captured items"),
    CAPTURED("Captured note", "A normal inbox item awaiting approval"),
    AUDIO("Audio note", "An audio item waiting for explicit transcription"),
    LONG_TEXT("Long transcript", "A long Russian transcript for typography checks"),
    ERROR("Storage error", "A recoverable error state"),
}

data class StudioUiState(
    val scenario: StudioScenario = StudioScenario.CAPTURED,
    val items: List<UiInboxItem> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = false,
    val simulated: Boolean = true,
    val approvals: Set<String> = emptySet(),
    val recording: StudioRecording = StudioRecording.IDLE,
    val editingTranscriptId: String? = null,
    val pendingApproval: PendingApproval? = null,
    val sync: StudioSyncState = StudioSyncState(),
    val board: UiTaskBoardState = defaultBoard(),
    val dailyPlan: UiDailyPlanState = defaultDailyPlan(),
) {
    fun toUiInboxState(): UiInboxState = UiInboxState(
        items = items.map { item ->
            item.copy(isTranscriptEditing = item.id == editingTranscriptId)
        },
        error = error,
        isLoading = isLoading,
        pendingApproval = pendingApproval,
    )
}

private fun defaultBoard() = UiTaskBoardState(
    title = "Project board / Personal Assistant",
    columns = listOf(
        UiTaskColumn("backlog", "Backlog"),
        UiTaskColumn("ready", "Ready"),
        UiTaskColumn("progress", "In progress", listOf(UiTaskCard("task-1", "Подготовить план проекта", "AndroidOS", "Friday", "High"))),
        UiTaskColumn("done", "Done"),
    ),
)

private fun defaultDailyPlan() = UiDailyPlanState(
    title = "Today",
    subtitle = "2026-09-29 · Tuesday",
    buckets = listOf(
        UiPlanBucket(
            "OVERDUE",
            "Overdue",
            listOf(UiPlanItem("plan-1", "Отправить отчёт за прошлую неделю", "2026-09-27", "High")),
        ),
        UiPlanBucket(
            "TODAY",
            "Today",
            listOf(
                UiPlanItem("plan-2", "Позвонить Анне и обсудить встречу", "2026-09-29", "High"),
                UiPlanItem("plan-3", "Проверить макет инбокса", "2026-09-29", "Medium"),
            ),
        ),
        UiPlanBucket(
            "UPCOMING",
            "Upcoming",
            listOf(UiPlanItem("plan-4", "Встреча с командой", "2026-10-02")),
        ),
        UiPlanBucket(
            "NO_DUE",
            "No due date",
            listOf(UiPlanItem("plan-5", "Идея: голосовые шаблоны для быстрых задач")),
        ),
    ),
)

data class StudioSyncConflict(
    val id: String,
    val field: String,
    val localValue: String,
    val remoteValue: String,
)

data class StudioSyncState(
    val status: String = "IDLE",
    val pendingChanges: Int = 0,
    val conflicts: List<StudioSyncConflict> = emptyList(),
)

/** Synthetic recording lifecycle for UI/UX testing; no MediaRecorder side effects. */
enum class StudioRecording(val label: String) {
    IDLE("Record"),
    RECORDING("Stop"),
}

sealed interface StudioAction {
    data class SelectScenario(val scenario: StudioScenario) : StudioAction
    data class Approve(val id: String, val destination: String) : StudioAction
    data class Transcribe(val id: String) : StudioAction
    data class BeginTranscriptEdit(val id: String) : StudioAction
    data class SaveTranscriptEdit(val id: String, val text: String) : StudioAction
    data class CancelTranscriptEdit(val id: String) : StudioAction
    data class Delete(val id: String) : StudioAction
    data class RequestApprove(val id: String, val kind: String) : StudioAction
    data class ConfirmApproval(val id: String, val kind: String) : StudioAction
    data object CancelApproval : StudioAction
    data object StartSync : StudioAction
    data class ResolveConflict(val id: String, val useRemote: Boolean) : StudioAction
    data object ToggleRecording : StudioAction
    data object Retry : StudioAction
    data object Reset : StudioAction
    data class MoveTask(val taskId: String, val targetColumnId: String) : StudioAction
    data class CompleteTask(val taskId: String) : StudioAction
}

fun reduceStudio(state: StudioUiState, action: StudioAction): StudioUiState = when (action) {
    is StudioAction.SelectScenario -> action.scenario.toUiState()
    is StudioAction.Approve -> if (action.destination in setOf("task", "event") && state.items.any { it.id == action.id } &&
        "${action.destination}:${action.id}" !in state.approvals) {
        state.copy(approvals = state.approvals + "${action.destination}:${action.id}")
    } else state
    is StudioAction.Transcribe -> state.copy(
        items = state.items.map { item ->
            if (item.id == action.id && item.kind == "AUDIO") {
                item.copy(
                    transcriptText = "Позвонить Анне и обсудить встречу завтра",
                    transcriptStatus = "RAW",
                )
            } else item
        },
    )
    is StudioAction.BeginTranscriptEdit -> state.copy(editingTranscriptId = action.id)
    is StudioAction.SaveTranscriptEdit -> state.copy(
        editingTranscriptId = null,
        items = state.items.map { item ->
            if (item.id == action.id && item.kind == "AUDIO") {
                item.copy(transcriptText = action.text, transcriptStatus = "EDITED")
            } else item
        },
    )
    is StudioAction.CancelTranscriptEdit -> state.copy(editingTranscriptId = null)
    is StudioAction.Delete -> state.copy(items = state.items.filterNot { it.id == action.id })
    is StudioAction.RequestApprove -> state.copy(
        pendingApproval = state.items.firstOrNull { it.id == action.id }?.let { item ->
            PendingApproval(action.id, action.kind, item.transcriptText ?: item.body)
        },
    )
    is StudioAction.ConfirmApproval -> state.copy(
        pendingApproval = null,
        approvals = state.approvals + "${action.kind.lowercase()}:${action.id}",
    )
    StudioAction.CancelApproval -> state.copy(pendingApproval = null)
    StudioAction.StartSync -> state.copy(
        sync = state.sync.copy(status = "CONFLICT", pendingChanges = 2, conflicts = listOf(
            StudioSyncConflict("conflict-1", "title", "Встреча сегодня", "Встреча завтра"),
        )),
    )
    is StudioAction.ResolveConflict -> state.copy(
        sync = state.sync.copy(
            status = "SYNCED",
            pendingChanges = 0,
            conflicts = state.sync.conflicts.filterNot { it.id == action.id },
        ),
    )
    StudioAction.ToggleRecording -> when (state.recording) {
        StudioRecording.IDLE -> state.copy(recording = StudioRecording.RECORDING)
        StudioRecording.RECORDING -> state.copy(
            recording = StudioRecording.IDLE,
            items = state.items + studioItem(
                id = "demo-rec",
                body = "Запись голосом (синтетическая)",
                stateLabel = "CAPTURED",
                capturedLabel = "13:00:00",
            ),
        )
    }
    StudioAction.Retry -> if (state.error != null) state.copy(error = null, items = StudioScenario.CAPTURED.toUiState().items) else state
    StudioAction.Reset -> state.scenario.toUiState()
    is StudioAction.MoveTask -> state.copy(board = moveTask(state.board, action.taskId, action.targetColumnId))
    is StudioAction.CompleteTask -> state.copy(dailyPlan = completePlanTask(state.dailyPlan, action.taskId))
}

private fun completePlanTask(plan: UiDailyPlanState, taskId: String): UiDailyPlanState =
    plan.copy(buckets = plan.buckets.map { bucket ->
        bucket.copy(items = bucket.items.filterNot { it.id == taskId })
    })

private fun moveTask(board: UiTaskBoardState, taskId: String, targetColumnId: String): UiTaskBoardState {
    val task = board.columns.flatMap { it.cards }.firstOrNull { it.id == taskId } ?: return board
    return board.copy(columns = board.columns.map { column ->
        when {
            column.id == targetColumnId -> column.copy(cards = column.cards + task)
            column.cards.any { it.id == taskId } -> column.copy(cards = column.cards.filterNot { it.id == taskId })
            else -> column
        }
    })
}

fun replayStudio(initialScenario: StudioScenario, actions: List<StudioAction>): StudioUiState =
    actions.fold(initialScenario.toUiState(), ::reduceStudio)

private fun studioItem(id: String, body: String, stateLabel: String, capturedLabel: String = "12:42:08") =
    UiInboxItem(id = id, body = body, stateLabel = stateLabel, capturedLabel = capturedLabel)

fun StudioScenario.toUiState(): StudioUiState = when (this) {
    StudioScenario.EMPTY -> StudioUiState(scenario = this)
    StudioScenario.CAPTURED -> StudioUiState(
        scenario = this,
        items = listOf(studioItem("demo-1", "Позвонить Анне и обсудить встречу завтра", "CAPTURED")),
    )
    StudioScenario.AUDIO -> StudioUiState(
        scenario = this,
        items = listOf(
            studioItem("demo-audio", "demo-capture.m4a", "CAPTURED").copy(kind = "AUDIO"),
        ),
    )
    StudioScenario.LONG_TEXT -> StudioUiState(
        scenario = this,
        items = listOf(
            studioItem(
                "demo-long",
                "Нужно подготовить план проекта, проверить ограничения по срокам, " +
                    "согласовать встречу с командой и не забыть отправить итоговые материалы " +
                    "всем участникам после обсуждения.",
                "TRANSCRIPT_EDIT",
            ),
        ),
    )
    StudioScenario.ERROR -> StudioUiState(scenario = this, error = "Не удалось загрузить локальные записи")
}
