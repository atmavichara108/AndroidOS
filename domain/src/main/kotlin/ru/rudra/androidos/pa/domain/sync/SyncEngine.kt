package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.SyncEnvelope
import ru.rudra.androidos.pa.domain.port.LocalStore
import java.security.MessageDigest
import java.util.UUID

/**
 * Owns envelope assembly and application for the PA sync contract.
 * Ordering, idempotency and tamper detection live here (see
 * docs/privacy-and-sync.md); transport and cryptography stay replaceable
 * (signing/encryption fields are filled by the CryptoBox layer, step 2).
 */
data class SyncApplyReport(
    val envelopeId: String,
    val applied: Int,
    val duplicates: Int,
    val rejected: Boolean = false,
    val reason: String? = null,
)

class SyncEngine(private val deviceId: String) {

    fun buildEnvelope(
        sequence: Long,
        changes: List<Change>,
        tombstones: List<String> = emptyList(),
        recipientScope: String = "pa",
        createdAt: String,
        expiresAt: String? = null,
        keyId: String = "",
        signature: String = "",
        encryptionAlgorithms: String = "",
    ): SyncEnvelope {
        val ordered = canonicalOrder(changes)
        val sortedTombstones = tombstones.sorted()
        return SyncEnvelope(
            id = UUID.randomUUID().toString(),
            senderDeviceId = deviceId,
            recipientScope = recipientScope,
            sequence = sequence,
            changes = ordered,
            tombstones = sortedTombstones,
            createdAt = createdAt,
            expiresAt = expiresAt,
            keyId = keyId,
            signature = signature,
            encryptionAlgorithms = encryptionAlgorithms,
            bundleHash = bundleHash(ordered, sortedTombstones),
        )
    }

    /**
     * Applies an envelope idempotently. A tampered bundle (hash mismatch) is
     * rejected whole — nothing is applied. Duplicates (within the envelope or
     * against the store) are counted and skipped, never applied twice.
     */
    fun apply(envelope: SyncEnvelope, store: LocalStore): SyncApplyReport {
        if (envelope.expiresAt != null && envelope.expiresAt < nowString()) {
            return SyncApplyReport(envelope.id, 0, 0, rejected = true, reason = "expired")
        }
        val expectedHash = bundleHash(envelope.changes, envelope.tombstones)
        if (expectedHash != envelope.bundleHash) {
            return SyncApplyReport(envelope.id, 0, 0, rejected = true, reason = "bundleHash mismatch")
        }
        val seen = HashSet<String>()
        var applied = 0
        var duplicates = 0
        for (change in envelope.changes) {
            if (!seen.add(change.idempotencyKey)) {
                duplicates++
                continue
            }
            if (store.applyChange(change)) applied++ else duplicates++
        }
        return SyncApplyReport(envelope.id, applied, duplicates)
    }

    private fun canonicalOrder(changes: List<Change>): List<Change> =
        changes.sortedWith(compareBy({ it.occurredAt }, { it.id }))

    private fun nowString(): String = java.time.Instant.now().toString()

    private fun bundleHash(changes: List<Change>, tombstones: List<String>): String {
        val canonical = buildString {
            changes.forEach { c ->
                append(c.id).append('|')
                append(c.entityId).append('|')
                append(c.operation.name).append('|')
                c.patch.toSortedMap().forEach { (k, v) -> append(k).append('=').append(v).append(';') }
                append('|').append(c.actorDeviceId)
                append('|').append(c.baseVersion ?: "")
                append('|').append(c.occurredAt)
                append('|').append(c.logicalClock ?: "")
                append('|').append(c.idempotencyKey)
                append('|').append(c.retentionClass.name)
                append('\n')
            }
            tombstones.forEach { append("T:").append(it).append('\n') }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
