package ru.rudra.androidos.pa.domain.intent

/**
 * Confidence-gated clarifying questions (docs/roadmap.md: personal
 * productivity contour). When the classifier or project resolver is unsure,
 * the host must ask the user rather than guess. This planner turns an
 * uncertain [IntentGuess] + [ProjectResolution] into the minimal set of
 * questions to ask, so the product stays safe and predictable (per the
 * evolutionary-approach note: confidence-gated routing).
 *
 * Pure and testable: no storage, no UI. The threshold is a policy knob, so a
 * future evolution layer can tune it from real accept/reject behaviour.
 */
enum class ClarificationQuestion {
    /** "Что это: задача / событие / идея / заметка?" — asked when kind is unknown. */
    KIND,

    /** "Куда: создать новый проект / в существующий / без проекта?" */
    PROJECT,

    /** "Это повторяющееся?" */
    RECURRING,
}

object ClarificationPlanner {

    /**
     * Produces the questions to ask for [guess] and [resolution].
     *
     * - Asks [ClarificationQuestion.KIND] when the kind is unknown
     *   ([IntentKind.NO_INTENT]) OR the classifier is below the [threshold] for
     *   any kind, so a low-confidence EVENT/MEETING/TASK still offers to confirm
     *   its type instead of silently committing a guess.
     * - An un-attached task (no known project, not an explicit new project) below
     *   the [threshold] asks [ClarificationQuestion.PROJECT].
     * - A low-confidence task asks [ClarificationQuestion.RECURRING], since it
     *   might repeat even though it was not clearly classified as a habit.
     *
     * High-confidence, unambiguous cases produce no questions.
     */
    fun plan(
        guess: IntentGuess,
        resolution: ProjectResolution,
        threshold: Double = 0.6,
    ): List<ClarificationQuestion> {
        val questions = mutableListOf<ClarificationQuestion>()

        if (guess.kind == IntentKind.NO_INTENT || guess.confidence < threshold) {
            questions += ClarificationQuestion.KIND
        }

        val isTaskLike = guess.kind == IntentKind.TASK
        if (isTaskLike &&
            resolution.attachment == ProjectAttachment.NONE &&
            guess.confidence < threshold
        ) {
            questions += ClarificationQuestion.PROJECT
        }

        if (isTaskLike && guess.confidence < threshold) {
            questions += ClarificationQuestion.RECURRING
        }

        return questions
    }
}