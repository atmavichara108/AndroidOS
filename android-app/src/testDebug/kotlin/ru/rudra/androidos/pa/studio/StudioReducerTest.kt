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

    @Test fun audioCanBeTranscribedAndEdited() {
        val audio = StudioScenario.AUDIO.toUiState()
        val transcribed = reduceStudio(audio, StudioAction.Transcribe("demo-audio"))
        assertEquals("RAW", transcribed.items.single().transcriptStatus)
        assertTrue(transcribed.items.single().transcriptText.orEmpty().isNotBlank())

        val editing = reduceStudio(transcribed, StudioAction.BeginTranscriptEdit("demo-audio"))
        assertEquals("demo-audio", editing.editingTranscriptId)

        val edited = reduceStudio(
            editing,
            StudioAction.SaveTranscriptEdit("demo-audio", "Обсудить встречу завтра"),
        )
        assertEquals("EDITED", edited.items.single().transcriptStatus)
        assertEquals("Обсудить встречу завтра", edited.items.single().transcriptText)
        assertEquals(null, edited.editingTranscriptId)

        val cancelled = reduceStudio(editing, StudioAction.CancelTranscriptEdit("demo-audio"))
        assertEquals(null, cancelled.editingTranscriptId)
    }

    @Test fun syncConflictCanBeResolved() {
        val conflicted = reduceStudio(StudioScenario.CAPTURED.toUiState(), StudioAction.StartSync)
        assertEquals("CONFLICT", conflicted.sync.status)
        assertTrue(conflicted.sync.conflicts.isNotEmpty())
        val resolved = reduceStudio(
            conflicted,
            StudioAction.ResolveConflict("conflict-1", useRemote = true),
        )
        assertEquals("SYNCED", resolved.sync.status)
        assertTrue(resolved.sync.conflicts.isEmpty())
    }

    @Test fun approvalRequiresExplicitConfirmation() {
        val requested = reduceStudio(
            StudioScenario.CAPTURED.toUiState(),
            StudioAction.RequestApprove("demo-1", "TASK"),
        )
        assertEquals("demo-1", requested.pendingApproval?.id)
        assertTrue(requested.approvals.isEmpty())

        val cancelled = reduceStudio(requested, StudioAction.CancelApproval)
        assertEquals(null, cancelled.pendingApproval)
        assertTrue(cancelled.approvals.isEmpty())

        val confirmed = reduceStudio(
            requested,
            StudioAction.ConfirmApproval("demo-1", "TASK"),
        )
        assertEquals(null, confirmed.pendingApproval)
        assertTrue(confirmed.approvals.contains("task:demo-1"))
    }
}
