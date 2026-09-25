package ru.rudra.androidos.pa.recording

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.rudra.androidos.pa.domain.statemachine.CaptureState

/**
 * In-process bridge between RecordingService and UI. The service publishes
 * every CaptureState transition here; the UI collects [state]. Commands go
 * through service intents (start/pause/resume/stop), not through this bus.
 */
object RecordingBus {
    val state: MutableStateFlow<CaptureState> = MutableStateFlow(CaptureState.IDLE)
}
