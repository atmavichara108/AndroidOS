package ru.rudra.androidos.pa.domain.model

data class SyncEnvelope(
    val id: String,
    val senderDeviceId: String,
    val recipientScope: String,
    val sequence: Long,
    val changes: List<Change>,
    val tombstones: List<String>,
    val createdAt: String,
    val expiresAt: String?,
    val keyId: String,
    val signature: String,
    val encryptionAlgorithms: String,
    val bundleHash: String,
)
