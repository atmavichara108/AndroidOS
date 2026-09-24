package ru.rudra.androidos.pa.domain.model

data class InboxItem(
    val id: String,
    val kind: InboxKind,
    val state: InboxState,
    val transcriptId: String?,
    val body: String?,
    val sourceDeviceId: String,
    val capturedAt: String,
    val createdAt: String,
    val updatedAt: String,
    val provenance: List<ProvenanceEntry>,
    val retentionClass: RetentionClass,
    val version: Long,
    val deletedAt: String? = null,
)

enum class InboxKind { AUDIO, TEXT, IMPORT }

enum class InboxState { CAPTURED, TRANSCRIBED, EDITED, STRUCTURED, ARCHIVED }

enum class RetentionClass { PERMANENT, TEMPORARY_AUDIO, TEMPORARY_TRANSCRIPT, SESSION }
