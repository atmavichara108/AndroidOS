package ru.rudra.androidos.pa.domain.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentClassifierTest {

    private val classifier = IntentClassifier()

    @Test
    fun `verb marks a task`() {
        val guess = classifier.classify("купить молоко и хлеб")
        assertEquals(IntentKind.TASK, guess.kind)
        assertTrue(guess.confidence >= 0.5)
    }

    @Test
    fun `meeting marker wins`() {
        val guess = classifier.classify("митинг по проекту завтра в десять")
        assertEquals(IntentKind.MEETING, guess.kind)
    }

    @Test
    fun `event marker wins`() {
        val guess = classifier.classify("встреча с врачом в три часа")
        assertEquals(IntentKind.EVENT, guess.kind)
    }

    @Test
    fun `project marker classifies as project`() {
        val guess = classifier.classify("начать проект новый сайт")
        assertEquals(IntentKind.PROJECT, guess.kind)
    }

    @Test
    fun `recurring marker classifies as habit`() {
        val guess = classifier.classify("заниматься спортом ежедневно")
        assertEquals(IntentKind.HABIT, guess.kind)
    }

    @Test
    fun `weekday alone classifies as habit`() {
        val guess = classifier.classify("тренировка в понедельник")
        assertEquals(IntentKind.HABIT, guess.kind)
    }

    @Test
    fun `idea marker classifies as idea`() {
        val guess = classifier.classify("идея для приложения")
        assertEquals(IntentKind.IDEA, guess.kind)
    }

    @Test
    fun `no marker yields note`() {
        val guess = classifier.classify("хорошая погода сегодня за окном")
        assertEquals(IntentKind.NOTE, guess.kind)
        assertEquals(0.0, guess.confidence)
    }

    @Test
    fun `empty text yields no intent`() {
        val guess = classifier.classify("   ")
        assertEquals(IntentKind.NO_INTENT, guess.kind)
        assertEquals(0.0, guess.confidence)
    }

    @Test
    fun `verb outranks weekday on a tie`() {
        val guess = classifier.classify("сделать отчет в пятницу")
        // one verb marker (сделать) vs one weekday marker (пятница): verb wins
        assertEquals(IntentKind.TASK, guess.kind)
    }

    @Test
    fun `confidence is share of matched markers`() {
        val guess = classifier.classify("сделать и купить")
        // two task markers, no others -> confidence 1.0
        assertEquals(IntentKind.TASK, guess.kind)
        assertEquals(1.0, guess.confidence)
    }

    @Test
    fun `matched markers are reported`() {
        val guess = classifier.classify("купить молоко")
        assertEquals(setOf("купить"), guess.matchedMarkers)
    }
}