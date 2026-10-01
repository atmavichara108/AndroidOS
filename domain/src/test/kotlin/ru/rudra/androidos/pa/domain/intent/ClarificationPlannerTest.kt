package ru.rudra.androidos.pa.domain.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClarificationPlannerTest {

    private val noProject = ProjectResolution(ProjectAttachment.NONE)

    @Test
    fun `unknown kind asks what it is`() {
        val guess = IntentGuess(IntentKind.NO_INTENT, 0.0, emptySet())
        val q = ClarificationPlanner.plan(guess, noProject)
        assertEquals(listOf(ClarificationQuestion.KIND), q)
    }

    @Test
    fun `low confidence unattached task asks project and recurring`() {
        val guess = IntentGuess(IntentKind.TASK, 0.5, setOf("купить"))
        val q = ClarificationPlanner.plan(guess, noProject)
        assertTrue(ClarificationQuestion.PROJECT in q)
        assertTrue(ClarificationQuestion.RECURRING in q)
    }

    @Test
    fun `low confidence task attached to known project asks only recurring`() {
        val guess = IntentGuess(IntentKind.TASK, 0.5, setOf("купить"))
        val attached = ProjectResolution(ProjectAttachment.KNOWN, projectId = "p1")
        val q = ClarificationPlanner.plan(guess, attached)
        assertTrue(ClarificationQuestion.RECURRING in q)
        assertTrue(ClarificationQuestion.PROJECT !in q)
    }

    @Test
    fun `high confidence task asks nothing`() {
        val guess = IntentGuess(IntentKind.TASK, 0.9, setOf("купить"))
        val q = ClarificationPlanner.plan(guess, noProject)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `new project task does not ask project question`() {
        val guess = IntentGuess(IntentKind.TASK, 0.5, setOf("сделать"))
        val newProject = ProjectResolution(ProjectAttachment.NEW)
        val q = ClarificationPlanner.plan(guess, newProject)
        assertTrue(ClarificationQuestion.PROJECT !in q)
    }

    @Test
    fun `low confidence habit asks only kind`() {
        // Low confidence for any kind asks to confirm the kind; HABIT is not
        // task-like, so no PROJECT/RECURRING question is added.
        val guess = IntentGuess(IntentKind.HABIT, 0.4, setOf("ежедневно"))
        val q = ClarificationPlanner.plan(guess, noProject)
        assertEquals(listOf(ClarificationQuestion.KIND), q)
    }

    @Test
    fun `high confidence non-task asks nothing`() {
        // A confidently-classified EVENT above threshold needs no confirmation.
        val guess = IntentGuess(IntentKind.EVENT, 0.9, setOf("встреча"))
        val q = ClarificationPlanner.plan(guess, noProject)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `low confidence event asks kind`() {
        // The device finding: a low-confidence EVENT should offer to confirm
        // its type instead of silently committing the guess.
        val guess = IntentGuess(IntentKind.EVENT, 0.5, setOf("встреча"))
        val q = ClarificationPlanner.plan(guess, noProject)
        assertEquals(listOf(ClarificationQuestion.KIND), q)
    }

    @Test
    fun `custom threshold changes gating`() {
        val guess = IntentGuess(IntentKind.TASK, 0.7, setOf("купить"))
        val strict = ClarificationPlanner.plan(guess, noProject, threshold = 0.8)
        assertTrue(ClarificationQuestion.PROJECT in strict)
        val loose = ClarificationPlanner.plan(guess, noProject, threshold = 0.5)
        assertTrue(loose.isEmpty())
    }
}