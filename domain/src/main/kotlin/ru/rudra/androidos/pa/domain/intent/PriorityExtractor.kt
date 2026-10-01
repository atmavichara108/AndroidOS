package ru.rudra.androidos.pa.domain.intent

/**
 * Rule-first priority extraction from a capture's text (docs/roadmap.md:
 * personal productivity contour). Mirrors the other intent primitives
 * ([ReminderPlanner], [ProjectResolver]): deterministic, offline, token/stem
 * based over lowercased text, no ML.
 *
 * Returns "HIGH", "LOW", or null (no explicit cue). Negated urgency ("не срочно",
 * "не важно") is treated as LOW and is checked before the positive urgency cues,
 * so "не срочно" does not read as "срочно".
 */
object PriorityExtractor {

    private val lowCues = listOf(
        "не срочно", "несрочно", "не горит", "не важно", "неважно",
        "когда-нибудь", "когда нибудь", "при случае", "потом", "низкий приоритет",
    )

    private val highCues = listOf(
        "срочно", "срочн", "важно", "важн", "критич", "немедленно",
        "горит", "безотлагательно", "как можно скорее", "высокий приоритет", "asap",
    )

    fun extract(text: String): String? {
        val lower = text.lowercase()
        if (lowCues.any { lower.contains(it) }) return "LOW"
        if (highCues.any { lower.contains(it) }) return "HIGH"
        return null
    }
}
