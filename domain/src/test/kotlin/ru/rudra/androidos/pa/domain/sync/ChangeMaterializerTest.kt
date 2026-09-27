package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.InboxKind
import ru.rudra.androidos.pa.domain.model.InboxState
import ru.rudra.androidos.pa.domain.model.RetentionClass

class ChangeMaterializerTest {

    private fun change(
        op: ChangeOperation,
        entityId: String,
        patch: Map<String, String>,
        id: String = "c-$entityId",
        occurredAt: String = "2026-09-26T10:00:00Z",
    ) = Change(
        id = id,
        entityId = entityId,
        operation = op,
        patch = patch,
        actorDeviceId = "laptop-peer",
        baseVersion = null,
        occurredAt = occurredAt,
        logicalClock = null,
        idempotencyKey = "k-$id",
        provenance = emptyList(),
        retentionClass = RetentionClass.PERMANENT,
    )

    @Test
    fun `entity create materializes from kind and title`() {
        val op = ChangeMaterializer.materialize(
            change(ChangeOperation.CREATE, "e-1", mapOf("kind" to "TASK", "title" to "позвонить"))
        )
        assertEquals(MaterializeOp.UpsertEntity("e-1", "TASK", "позвонить", "APPROVED"), op)
    }

    @Test
    fun `entity create keeps sender status`() {
        val op = ChangeMaterializer.materialize(
            change(ChangeOperation.CREATE, "e-2", mapOf("kind" to "EVENT", "title" to "встреча", "status" to "PROPOSED"))
        )
        assertEquals(MaterializeOp.UpsertEntity("e-2", "EVENT", "встреча", "PROPOSED"), op)
    }

    @Test
    fun `inbox create materializes from body`() {
        val op = ChangeMaterializer.materialize(
            change(
                ChangeOperation.CREATE,
                "in-1",
                mapOf(
                    "body" to "заметка",
                    "kind" to InboxKind.TEXT.name,
                    "state" to InboxState.CAPTURED.name,
                    "sourceDeviceId" to "laptop-peer",
                    "capturedAt" to "2026-09-26T10:00:00Z",
                    "createdAt" to "2026-09-26T10:00:00Z",
                    "updatedAt" to "2026-09-26T10:00:00Z",
                    "retentionClass" to RetentionClass.PERMANENT.name,
                )
            )
        )
        val inbox = op as MaterializeOp.UpsertInbox
        assertEquals("in-1", inbox.id)
        assertEquals(InboxKind.TEXT.name, inbox.kind)
        assertEquals("заметка", inbox.body)
        assertEquals("laptop-peer", inbox.sourceDeviceId)
    }

    @Test
    fun `inbox create falls back to defaults for omitted fields`() {
        val op = ChangeMaterializer.materialize(
            change(ChangeOperation.CREATE, "in-2", mapOf("body" to "короткая"))
        )
        val inbox = op as MaterializeOp.UpsertInbox
        assertEquals(InboxKind.TEXT.name, inbox.kind)
        assertEquals(InboxState.CAPTURED.name, inbox.state)
        assertEquals("laptop-peer", inbox.sourceDeviceId)
        assertEquals(RetentionClass.PERMANENT.name, inbox.retentionClass)
    }

@Test
fun `transcript update materializes text and status`() {
        val op = ChangeMaterializer.materialize(
            change(
                ChangeOperation.UPDATE,
                "in-3",
                mapOf(
                    "transcriptId" to "t-3",
                    "transcriptText" to "один два три",
                    "transcriptStatus" to "RAW",
                )
            )
        )
        assertEquals(MaterializeOp.UpsertTranscript("t-3", "in-3", "один два три", "RAW"), op)
    }

    @Test
    fun `transcript update carries inbox state transition`() {
        val op = ChangeMaterializer.materialize(
            change(
                ChangeOperation.UPDATE,
                "in-3b",
                mapOf(
                    "transcriptId" to "t-3b",
                    "transcriptText" to "текст",
                    "transcriptStatus" to "RAW",
                    "state" to InboxState.TRANSCRIBED.name,
                )
            )
        )
        assertEquals(MaterializeOp.UpsertTranscript("t-3b", "in-3b", "текст", "RAW"), op)
    }

    @Test
    fun `tombstone materializes deletedAt`() {
        val op = ChangeMaterializer.materialize(
            change(ChangeOperation.TOMBSTONE, "in-4", mapOf("deletedAt" to "2026-09-26T11:00:00Z"))
        )
        assertEquals(MaterializeOp.Tombstone("in-4", "2026-09-26T11:00:00Z"), op)
    }

    @Test
    fun `tombstone without deletedAt uses occurredAt`() {
        val op = ChangeMaterializer.materialize(
            change(ChangeOperation.TOMBSTONE, "in-5", emptyMap(), occurredAt = "2026-09-26T12:00:00Z")
        )
        assertEquals(MaterializeOp.Tombstone("in-5", "2026-09-26T12:00:00Z"), op)
    }

    @Test
    fun `create with no kind and no body is unsupported`() {
        val op = ChangeMaterializer.materialize(change(ChangeOperation.CREATE, "x", emptyMap()))
        assertTrue(op is MaterializeOp.Unsupported)
    }

@Test
fun `entity update materializes from kind and title`() {
        val op = ChangeMaterializer.materialize(
            change(
                ChangeOperation.UPDATE,
                "peer-entity-con2",
                mapOf("kind" to "TASK", "title" to "ЗАДАЧА-ПЕРЕЗАПИСЬ", "status" to "APPROVED")
            )
        )
        assertEquals(MaterializeOp.UpsertEntity("peer-entity-con2", "TASK", "ЗАДАЧА-ПЕРЕЗАПИСЬ", "APPROVED"), op)
    }

    @Test
    fun `inbox update with kind TEXT does not route to entity`() {
        val op = ChangeMaterializer.materialize(
            change(
                ChangeOperation.UPDATE,
                "in-9",
                mapOf("kind" to InboxKind.TEXT.name, "state" to InboxState.CAPTURED.name)
            )
        )
        assertTrue(op is MaterializeOp.Unsupported)
    }

    @Test
    fun `delete operation is unsupported by PA`() {
        val op = ChangeMaterializer.materialize(change(ChangeOperation.DELETE, "x", emptyMap()))
        assertTrue(op is MaterializeOp.Unsupported)
    }
}