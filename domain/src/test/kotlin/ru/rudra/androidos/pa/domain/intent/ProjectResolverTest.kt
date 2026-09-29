package ru.rudra.androidos.pa.domain.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectResolverTest {

    private val projects = listOf(
        ProjectInfo("p1", "Сайт"),
        ProjectInfo("p2", "Ремонт квартиры"),
    )

    @Test
    fun `task mentioning existing project attaches to known project`() {
        val r = ProjectResolver.resolve("сделать раздел для сайта", IntentKind.TASK, projects)
        assertEquals(ProjectAttachment.KNOWN, r.attachment)
        assertEquals("p1", r.projectId)
        assertEquals("Сайт", r.matchedTitle)
    }

    @Test
    fun `project intent with no known match means new project`() {
        val r = ProjectResolver.resolve("начать проект новая идея", IntentKind.PROJECT, projects)
        assertEquals(ProjectAttachment.NEW, r.attachment)
        assertNull(r.projectId)
    }

    @Test
    fun `plain task without project mention is none`() {
        val r = ProjectResolver.resolve("купить молоко", IntentKind.TASK, projects)
        assertEquals(ProjectAttachment.NONE, r.attachment)
        assertNull(r.projectId)
    }

    @Test
    fun `matching is case insensitive`() {
        val r = ProjectResolver.resolve("про сайт сделать правки", IntentKind.TASK, projects)
        assertEquals(ProjectAttachment.KNOWN, r.attachment)
        assertEquals("p1", r.projectId)
    }

    @Test
    fun `multiword project title matched as whole`() {
        val r = ProjectResolver.resolve("когда ремонт квартиры доделаем", IntentKind.TASK, projects)
        assertEquals(ProjectAttachment.KNOWN, r.attachment)
        assertEquals("p2", r.projectId)
    }

    @Test
    fun `empty known projects never attaches`() {
        val r = ProjectResolver.resolve("купить молоко", IntentKind.TASK, emptyList())
        assertEquals(ProjectAttachment.NONE, r.attachment)
    }

    @Test
    fun `habit is recurring`() {
        assertTrue(ProjectResolver.isRecurring(IntentKind.HABIT))
        assertTrue(!ProjectResolver.isRecurring(IntentKind.TASK))
    }

    @Test
    fun `project intent beats no matching for new`() {
        // PROJECT intent with no mention of any known project -> NEW, even if
        // another kind would normally be NONE.
        val r = ProjectResolver.resolve("новый проект блог", IntentKind.PROJECT, projects)
        assertEquals(ProjectAttachment.NEW, r.attachment)
    }

    @Test
    fun `known project mention beats project-intent new`() {
        val r = ProjectResolver.resolve("проект сайт надо расширить", IntentKind.PROJECT, projects)
        assertEquals(ProjectAttachment.KNOWN, r.attachment)
        assertEquals("p1", r.projectId)
    }
}