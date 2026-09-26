package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.ProvenanceEntry
import ru.rudra.androidos.pa.domain.model.ProvenanceSource
import ru.rudra.androidos.pa.domain.model.RetentionClass

class EnvelopeCodecTest {

    private val engine = SyncEngine("phone-1")

    private val keys = CryptoBox.deriveKeys("phrase".toCharArray(), ExchangeFile.saltFor("pa-local"))

    private fun sampleChange(id: String) = Change(
        id = "change-$id",
        entityId = "entity-$id",
        operation = ChangeOperation.CREATE,
        patch = mapOf("title" to "задача $id", "note" to "строка с юникодом ✓"),
        actorDeviceId = "phone-1",
        baseVersion = 7L,
        occurredAt = "2026-09-26T10:00:01Z",
        logicalClock = "clock-1",
        idempotencyKey = "key-$id",
        provenance = listOf(
            ProvenanceEntry(
                source = ProvenanceSource.STT_ENGINE,
                actor = "sherpa",
                at = "2026-09-26T10:00:00Z",
                detail = "ru",
            )
        ),
        retentionClass = RetentionClass.TEMPORARY_TRANSCRIPT,
    )

    @Test
    fun `envelope survives encode-decode round trip`() {
        val envelope = engine.buildEnvelope(
            sequence = 42,
            changes = listOf(sampleChange("a"), sampleChange("b")),
            tombstones = listOf("entity-x"),
            createdAt = "2026-09-26T10:01:00Z",
            expiresAt = "2026-12-31T00:00:00Z",
            keyId = "pa-local",
            encryptionAlgorithms = "AES-256-GCM+HMAC-SHA256",
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(envelope))
        assertEquals(envelope, decoded)
    }

    @Test
    fun `change list survives encode-decode round trip`() {
        val changes = listOf(sampleChange("a"), sampleChange("b"))
        assertEquals(changes, EnvelopeCodec.decodeChanges(EnvelopeCodec.encodeChanges(changes)))
    }

    @Test
    fun `exchange file round trips through crypto`() {
        val envelope = engine.buildEnvelope(
            sequence = 1,
            changes = listOf(sampleChange("a")),
            createdAt = "T",
            keyId = "pa-local",
        )
        val file = kotlin.io.path.createTempFile("pa-sync", ".pasync").toFile()
        try {
            ExchangeFile.write(file, envelope, keys)
            val header = ExchangeFile.readHeader(file)
            assertNotNull(header)
            assertEquals(envelope.id, header.id)
            assertEquals(engine.let { envelope.senderDeviceId }, header.sender)

            val read = ExchangeFile.read(file, keys)
            assertEquals(envelope, read)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `exchange file with wrong key is unreadable`() {
        val envelope = engine.buildEnvelope(1, listOf(sampleChange("a")), createdAt = "T", keyId = "pa-local")
        val file = kotlin.io.path.createTempFile("pa-sync", ".pasync").toFile()
        try {
            ExchangeFile.write(file, envelope, keys)
            val other = CryptoBox.deriveKeys("wrong".toCharArray(), ExchangeFile.saltFor("pa-local"))
            assertNull(ExchangeFile.read(file, other))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `exchange file with tampered payload is rejected`() {
        val envelope = engine.buildEnvelope(1, listOf(sampleChange("a")), createdAt = "T", keyId = "pa-local")
        val file = kotlin.io.path.createTempFile("pa-sync", ".pasync").toFile()
        try {
            ExchangeFile.write(file, envelope, keys)
            val text = file.readText()
            val twisted = text.replaceFirst("payload: ", "payload: A")
            file.writeText(twisted)
            assertNull(ExchangeFile.read(file, keys))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `non pa-sync file is refused`() {
        val file = kotlin.io.path.createTempFile("pa-sync", ".pasync").toFile()
        try {
            file.writeText("hello\n")
            assertNull(ExchangeFile.readHeader(file))
            assertTrue(ExchangeFile.read(file, keys) == null)
        } finally {
            file.delete()
        }
    }
}
