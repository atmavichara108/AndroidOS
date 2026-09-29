package ru.rudra.androidos.pa.domain.intent

/**
 * Decides how an intent attaches to a project (docs/roadmap.md: personal
 * productivity contour). A capture may belong to an existing project (matched
 * by name/title mentioned in the text), require a new project (e.g. an
 * explicit "start a project" intent), or belong to none (temporary/inbox).
 *
 * Pure and testable: no storage, no ML. The caller supplies the list of known
 * projects (id + title) from its own store, so this domain logic stays
 * deterministic and decoupled.
 *
 * Recurrence is reported separately: a recurring task/habit may be inside a
 * project or not, matching the classifier's HABIT kind.
 */
enum class ProjectAttachment { KNOWN, NEW, NONE }

data class ProjectInfo(val id: String, val title: String)

data class ProjectResolution(
    val attachment: ProjectAttachment,
    val projectId: String? = null,
    val matchedTitle: String? = null,
)

object ProjectResolver {

    /** Downstream flag: whether the intent is recurring (a habit or repeats). */
    fun isRecurring(intentKind: IntentKind): Boolean = intentKind == IntentKind.HABIT

    /**
     * Resolves project attachment for [text] given the classified [intentKind]
     * and the caller's known [projects].
     *
     * - A [IntentKind.PROJECT] intent means a new project should be created
     *   (a NEW attachment), unless an existing project is explicitly named.
     * - Otherwise, an existing project whose title (case-insensitive) appears
     *   in [text] yields a KNOWN attachment.
     * - Everything else is NONE (temporary inbox).
     */
    fun resolve(text: String, intentKind: IntentKind, projects: List<ProjectInfo>): ProjectResolution {
        val lower = text.lowercase()

        val known = projects.firstOrNull { p ->
            p.title.lowercase().let { title ->
                title.isNotBlank() && lower.contains(title)
            }
        }
        if (known != null) {
            return ProjectResolution(ProjectAttachment.KNOWN, projectId = known.id, matchedTitle = known.title)
        }

        if (intentKind == IntentKind.PROJECT) {
            return ProjectResolution(ProjectAttachment.NEW)
        }

        return ProjectResolution(ProjectAttachment.NONE)
    }
}