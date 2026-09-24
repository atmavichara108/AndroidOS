package ru.rudra.androidos.pa.domain.statemachine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureStateMachineTest {

    private fun sm(): CaptureStateMachine = CaptureStateMachine()

    @Test
    fun `start from idle starts recording`() {
        val r = sm().dispatch(CaptureCommand.Start)
        assertEquals(CaptureState.RECORDING, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `start is idempotent while recording`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        val r = m.dispatch(CaptureCommand.Start)
        assertEquals(CaptureState.RECORDING, r.state)
        assertFalse(r.changed)
    }

    @Test
    fun `pause from recording pauses`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        val r = m.dispatch(CaptureCommand.Pause)
        assertEquals(CaptureState.PAUSED, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `pause before start is a no-op`() {
        val r = sm().dispatch(CaptureCommand.Pause)
        assertEquals(CaptureState.IDLE, r.state)
        assertFalse(r.changed)
    }

    @Test
    fun `pause is idempotent while paused`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        m.dispatch(CaptureCommand.Pause)
        val r = m.dispatch(CaptureCommand.Pause)
        assertEquals(CaptureState.PAUSED, r.state)
        assertFalse(r.changed)
    }

    @Test
    fun `resume from paused returns to recording`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        m.dispatch(CaptureCommand.Pause)
        val r = m.dispatch(CaptureCommand.Resume)
        assertEquals(CaptureState.RECORDING, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `resume outside paused session is a no-op`() {
        val m = sm()
        val r = m.dispatch(CaptureCommand.Resume)
        assertEquals(CaptureState.IDLE, r.state)
        assertFalse(r.changed)
    }

    @Test
    fun `stop from recording stops`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        val r = m.dispatch(CaptureCommand.Stop)
        assertEquals(CaptureState.STOPPED, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `stop from paused stops`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        m.dispatch(CaptureCommand.Pause)
        val r = m.dispatch(CaptureCommand.Stop)
        assertEquals(CaptureState.STOPPED, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `stop is idempotent while stopped`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        m.dispatch(CaptureCommand.Stop)
        val r = m.dispatch(CaptureCommand.Stop)
        assertEquals(CaptureState.STOPPED, r.state)
        assertFalse(r.changed)
    }

    @Test
    fun `restart after stop is allowed`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        m.dispatch(CaptureCommand.Stop)
        val r = m.dispatch(CaptureCommand.Start)
        assertEquals(CaptureState.RECORDING, r.state)
        assertTrue(r.changed)
    }

    @Test
    fun `arbitrary duplicate command replay is stable`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        val l = listOf(CaptureCommand.Pause, CaptureCommand.Resume, CaptureCommand.Pause, CaptureCommand.Resume)
        val results = l.map { m.dispatch(it) }
        assertTrue(results.all { it.changed || it.state == m.current() })
        val s1 = m.current()
        val replay = l.map { m.dispatch(it) }
        // Replaying the same command sequence converges to the same terminal state
        assertEquals(s1, replay.last().state)
        assertEquals(CaptureState.RECORDING, m.current())
    }

    @Test
    fun `duplicate commands targeting current state are no-ops`() {
        val m = sm()
        m.dispatch(CaptureCommand.Start)
        assertFalse(m.dispatch(CaptureCommand.Start).changed)
        m.dispatch(CaptureCommand.Pause)
        assertFalse(m.dispatch(CaptureCommand.Pause).changed)
        m.dispatch(CaptureCommand.Resume)
        assertFalse(m.dispatch(CaptureCommand.Resume).changed)
        m.dispatch(CaptureCommand.Stop)
        assertFalse(m.dispatch(CaptureCommand.Stop).changed)
    }
}
