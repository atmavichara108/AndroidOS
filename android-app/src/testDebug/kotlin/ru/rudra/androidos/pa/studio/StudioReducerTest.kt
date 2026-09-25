package ru.rudra.androidos.pa.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioReducerTest {
    @Test fun approvalsAreIdempotent() {
        val initial = StudioScenario.CAPTURED.toUiState()
        val action = StudioAction.Approve("demo-1", "task")
        val once = reduceStudio(initial, action)
        assertEquals(once, reduceStudio(once, action))
        assertTrue(once.approvals.contains("task:demo-1"))
    }

    @Test fun retryResolvesError() {
        val recovered = reduceStudio(StudioScenario.ERROR.toUiState(), StudioAction.Retry)
        assertEquals(null, recovered.error)
        assertTrue(recovered.items.isNotEmpty())
        assertEquals(recovered, reduceStudio(recovered, StudioAction.Retry))
        assertEquals(StudioScenario.ERROR.toUiState(), reduceStudio(recovered, StudioAction.Reset))
    }

    @Test fun resetReturnsSelectedFixture() {
        val state = reduceStudio(StudioScenario.CAPTURED.toUiState(), StudioAction.Approve("demo-1", "event"))
        assertEquals(StudioScenario.CAPTURED.toUiState(), reduceStudio(state, StudioAction.Reset))
    }

    @Test fun replayReconstructsSemanticTransitions() {
        val actions = listOf(
            StudioAction.SelectScenario(StudioScenario.ERROR),
            StudioAction.Retry,
            StudioAction.Approve("demo-1", "task"),
            StudioAction.Approve("demo-1", "task"),
            StudioAction.Approve("demo-1", "event"),
        )
        val replayed = replayStudio(StudioScenario.EMPTY, actions)
        assertEquals(setOf("task:demo-1", "event:demo-1"), replayed.approvals)
        assertEquals(null, replayed.error)
        assertEquals(replayed, replayStudio(StudioScenario.EMPTY, actions))
    }

    @Test fun recordingToggleTransitionsAndCapturesOnStop() {
        val initial = StudioScenario.CAPTURED.toUiState()
        val recording = reduceStudio(initial, StudioAction.ToggleRecording)
        assertEquals(StudioRecording.RECORDING, recording.recording)
        val stopped = reduceStudio(recording, StudioAction.ToggleRecording)
        assertEquals(StudioRecording.IDLE, stopped.recording)
        assertTrue(stopped.items.any { it.id == "demo-rec" })
    }

    @Test fun invalidApprovalsDoNotMutateState() {
        val initial = StudioScenario.CAPTURED.toUiState()
        assertEquals(initial, reduceStudio(initial, StudioAction.Approve("missing", "task")))
        assertEquals(initial, reduceStudio(initial, StudioAction.Approve("demo-1", "invalid")))
        assertTrue(initial.approvals.isEmpty())
    }

    @Test fun selectingScenarioClearsPreviousMutations() {
        val approved = reduceStudio(StudioScenario.CAPTURED.toUiState(), StudioAction.Approve("demo-1", "task"))
        StudioScenario.entries.forEach {
            assertEquals(it.toUiState(), reduceStudio(approved, StudioAction.SelectScenario(it)))
        }
        assertEquals(StudioScenario.EMPTY.toUiState(), replayStudio(StudioScenario.EMPTY, emptyList()))
    }
}
