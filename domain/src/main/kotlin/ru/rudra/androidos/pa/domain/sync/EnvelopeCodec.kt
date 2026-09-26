package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.ProvenanceEntry
import ru.rudra.androidos.pa.domain.model.ProvenanceSource
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.model.SyncEnvelope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Deterministic binary encoding for [SyncEnvelope], used for the sealed
 * payload. No JSON/reflection dependency: the same bytes must be produced on
 * the phone and on the laptop peer, and the byte stream is what CryptoBox
 * authenticates.
 */
object EnvelopeCodec {

    private const val VERSION = 1

    fun encode(envelope: SyncEnvelope): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(VERSION)
            d.writeString(envelope.id)
            d.writeString(envelope.senderDeviceId)
            d.writeString(envelope.recipientScope)
            d.writeLong(envelope.sequence)
            d.writeString(envelope.createdAt)
            d.writeString(envelope.expiresAt ?: "")
            d.writeString(envelope.keyId)
            d.writeString(envelope.signature)
            d.writeString(envelope.encryptionAlgorithms)
            d.writeString(envelope.bundleHash)
            d.writeInt(envelope.changes.size)
            envelope.changes.forEach { d.writeChange(it) }
            d.writeInt(envelope.tombstones.size)
            envelope.tombstones.forEach { d.writeString(it) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): SyncEnvelope {
        DataInputStream(ByteArrayInputStream(bytes)).use { d ->
            val version = d.readInt()
            require(version == VERSION) { "unsupported envelope version $version" }
            val id = d.readString()
            val sender = d.readString()
            val scope = d.readString()
            val sequence = d.readLong()
            val createdAt = d.readString()
            val expiresAt = d.readString().ifEmpty { null }
            val keyId = d.readString()
            val signature = d.readString()
            val algorithms = d.readString()
            val bundleHash = d.readString()
            val changes = List(d.readInt()) { d.readChange() }
            val tombstones = List(d.readInt()) { d.readString() }
            return SyncEnvelope(
                id = id,
                senderDeviceId = sender,
                recipientScope = scope,
                sequence = sequence,
                changes = changes,
                tombstones = tombstones,
                createdAt = createdAt,
                expiresAt = expiresAt,
                keyId = keyId,
                signature = signature,
                encryptionAlgorithms = algorithms,
                bundleHash = bundleHash,
            )
        }
    }

    /** Persists a bare change list (used by the laptop peer's local state). */
    fun encodeChanges(changes: List<Change>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(VERSION)
            d.writeInt(changes.size)
            changes.forEach { d.writeChange(it) }
        }
        return out.toByteArray()
    }

    fun decodeChanges(bytes: ByteArray): List<Change> {
        DataInputStream(ByteArrayInputStream(bytes)).use { d ->
            val version = d.readInt()
            require(version == VERSION) { "unsupported change list version $version" }
            return List(d.readInt()) { d.readChange() }
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataOutputStream.writeChange(change: Change) {
        writeString(change.id)
        writeString(change.entityId)
        writeString(change.operation.name)
        val patchEntries = change.patch.entries.sortedBy { it.key }
        writeInt(patchEntries.size)
        patchEntries.forEach { (k, v) ->
            writeString(k)
            writeString(v)
        }
        writeString(change.actorDeviceId)
        writeLong(change.baseVersion ?: -1L)
        writeString(change.occurredAt)
        writeString(change.logicalClock ?: "")
        writeString(change.idempotencyKey)
        writeInt(change.provenance.size)
        change.provenance.forEach { p ->
            writeString(p.source.name)
            writeString(p.actor)
            writeString(p.at)
            writeString(p.detail ?: "")
        }
        writeString(change.retentionClass.name)
    }

    private fun DataInputStream.readChange(): Change {
        val id = readString()
        val entityId = readString()
        val operation = ChangeOperation.valueOf(readString())
        val patch = buildMap {
            repeat(readInt()) { put(readString(), readString()) }
        }
        val actor = readString()
        val baseVersion = readLong().let { if (it < 0) null else it }
        val occurredAt = readString()
        val logicalClock = readString().ifEmpty { null }
        val idempotencyKey = readString()
        val provenance = List(readInt()) {
            ProvenanceEntry(
                source = ProvenanceSource.valueOf(readString()),
                actor = readString(),
                at = readString(),
                detail = readString().ifEmpty { null },
            )
        }
        val retentionClass = RetentionClass.valueOf(readString())
        return Change(
            id = id,
            entityId = entityId,
            operation = operation,
            patch = patch,
            actorDeviceId = actor,
            baseVersion = baseVersion,
            occurredAt = occurredAt,
            logicalClock = logicalClock,
            idempotencyKey = idempotencyKey,
            provenance = provenance,
            retentionClass = retentionClass,
        )
    }
}
