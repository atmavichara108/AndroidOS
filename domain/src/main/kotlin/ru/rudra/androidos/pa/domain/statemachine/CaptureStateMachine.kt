package ru.rudra.androidos.pa.domain.statemachine

sealed interface CaptureCommand {
    data object Start : CaptureCommand
    data object Pause : CaptureCommand
    data object Resume : CaptureCommand
    data object Stop : CaptureCommand
}

enum class CaptureState { IDLE, RECORDING, PAUSED, STOPPED }

data class CaptureResult(
    val state: CaptureState,
    val changed: Boolean,
    val reason: String? = null,
)

data class RecordingSession(
    val inboxItemId: String,
    val state: CaptureState,
)

class CaptureStateMachine(initial: CaptureState = CaptureState.IDLE) {

    private var state: CaptureState = initial

    fun current(): CaptureState = state

    fun dispatch(cmd: CaptureCommand): CaptureResult {
        val next=CaptureResult(state,false,null)
        return when (cmd) {
            CaptureCommand.Start -> when (state) {
                CaptureState.IDLE, CaptureState.STOPPED ->
                    transition(CaptureState.RECORDING)
                CaptureState.RECORDING, CaptureState.PAUSED -> unchanged()
            }
            CaptureCommand.Pause -> when (state) {
                CaptureState.RECORDING -> transition(CaptureState.PAUSED)
                CaptureState.PAUSED -> unchanged()
                CaptureState.IDLE, CaptureState.STOPPED ->
                    unchanged("pause before start is a no-op")
            }
            CaptureCommand.Resume -> when (state) {
                CaptureState.PAUSED -> transition(CaptureState.RECORDING)
                CaptureState.RECORDING -> unchanged()
                CaptureState.IDLE, CaptureState.STOPPED ->
                    unchanged("resume outside paused session is a no-op")
            }
            CaptureCommand.Stop -> when (state) {
                CaptureState.RECORDING, CaptureState.PAUSED ->
                    transition(CaptureState.STOPPED)
                CaptureState.IDLE, CaptureState.STOPPED -> unchanged()
            }
        }
    }

    private fun transition(target: CaptureState): CaptureResult {
        state = target
        return CaptureResult(target, changed = true)
    }

    private fun unchanged(reason: String? = null): CaptureResult =
        CaptureResult(state, changed = false, reason = reason)
}
