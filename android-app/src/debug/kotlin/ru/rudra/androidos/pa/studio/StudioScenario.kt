package ru.rudra.androidos.pa.studio

import ru.rudra.androidos.pa.ui.UiInboxItem
import ru.rudra.androidos.pa.ui.UiInboxState

/** Synthetic, side-effect-free data used by the embedded UI laboratory. */
enum class StudioScenario(val title: String, val description: String) {
    EMPTY("Empty inbox", "No captured items"),
    CAPTURED("Captured note", "A normal inbox item awaiting approval"),
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
) {
    fun toUiInboxState(): UiInboxState = UiInboxState(items = items, error = error, isLoading = isLoading)
}

/** Synthetic recording lifecycle for UI/UX testing; no MediaRecorder side effects. */
enum class StudioRecording(val label: String) {
    IDLE("Record"),
    RECORDING("Stop"),
}

sealed interface StudioAction {
    data class SelectScenario(val scenario: StudioScenario) : StudioAction
    data class Approve(val id: String, val destination: String) : StudioAction
    data object ToggleRecording : StudioAction
    data object Retry : StudioAction
    data object Reset : StudioAction
}

fun reduceStudio(state: StudioUiState, action: StudioAction): StudioUiState = when (action) {
    is StudioAction.SelectScenario -> action.scenario.toUiState()
    is StudioAction.Approve -> if (action.destination in setOf("task", "event") && state.items.any { it.id == action.id } &&
        "${action.destination}:${action.id}" !in state.approvals) {
        state.copy(approvals = state.approvals + "${action.destination}:${action.id}")
    } else state
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
