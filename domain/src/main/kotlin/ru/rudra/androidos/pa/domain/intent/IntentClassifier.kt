package ru.rudra.androidos.pa.domain.intent

import ru.rudra.androidos.pa.domain.search.SearchTokens

/**
 * Deterministic, rule-based intent classifier (docs/roadmap.md: personal
 * productivity contour). Given a transcript, decides what kind of intent it
 * carries — a task, an event/meeting, a project, a habit (recurring), an idea
 * or a plain note — so the host can route it to the right downstream step
 * (project resolution, reminder/calendar, or a clarifying question).
 *
 * Pure and testable on the JVM: no ML, no storage, no UI. The rule table is
 * injectable so an optional ML model can be dropped in behind the same
 * [classify] surface later.
 *
 * Classification is marker-based: the transcript is tokenized with
 * [SearchTokens.normalize] and each marker keyword present votes for its kind.
 * The kind with the most votes wins. Confidence is the winning kind's share of
 * all votes, so a single clear marker scores higher than a mixture of
 * conflicting markers. No votes at all yields [IntentKind.NO_INTENT] with
 * confidence 0.
 */
enum class IntentKind { TASK, EVENT, MEETING, PROJECT, HABIT, IDEA, NOTE, NO_INTENT }

data class IntentGuess(
    val kind: IntentKind,
    val confidence: Double,
    val matchedMarkers: Set<String>,
)

class IntentClassifier(
    private val markers: Map<IntentKind, Set<String>> = defaultMarkers(),
) {

    fun classify(text: String): IntentGuess {
        val tokens = SearchTokens.normalize(text)
        if (tokens.isEmpty()) return IntentGuess(IntentKind.NO_INTENT, 0.0, emptySet())

        val votes = HashMap<IntentKind, MutableSet<String>>()
        for ((kind, keywords) in markers) {
            val matched = keywords.intersect(tokens)
            if (matched.isNotEmpty()) votes[kind] = matched.toMutableSet()
        }
        if (votes.isEmpty()) return IntentGuess(IntentKind.NOTE, 0.0, emptySet())

        val winning = votes.maxByOrNull { it.value.size }!!
        val totalMatched = votes.values.sumOf { it.size }
        val confidence = winning.value.size.toDouble() / totalMatched.toDouble()
        return IntentGuess(winning.key, confidence, winning.value.toSet())
    }

    companion object {
        fun defaultMarkers(): Map<IntentKind, Set<String>> = mapOf(
            IntentKind.TASK to setOf(
                "сделать", "купить", "позвонить", "проверить", "написать", "отправить",
                "оплатить", "записаться", "сходить", "встретиться", "приготовить",
                "убрать", "постирать", "выбросить", "оплата", "взять", "забрать",
            ),
            IntentKind.EVENT to setOf(
                "встреча", "созвон", "конференция", "событие", "назначить", "встречу",
            ),
            IntentKind.MEETING to setOf(
                "митинг", "планерка", "совещание", "ситион",
            ),
            IntentKind.PROJECT to setOf(
                "проект",
            ),
            IntentKind.HABIT to setOf(
                "ежедневно", "ежедневная", "еженедельно", "ежемесячно", "повторять",
                "повторяющееся", "повторная", "понедельник", "вторник", "среда",
                "четверг", "пятница", "суббота", "воскресенье",
            ),
            IntentKind.IDEA to setOf(
                "идея", "мысль", "придумать",
            ),
        )
    }
}