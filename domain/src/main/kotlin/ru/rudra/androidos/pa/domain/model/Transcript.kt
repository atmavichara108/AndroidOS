package ru.rudra.androidos.pa.domain.model

data class Transcript(
    val id: String,
    val inboxItemId: String,
    val text: String,
    val languageHints: List<String>,
    val segments: List<TranscriptSegment>,
    val engineId: String,
    val modelId: String,
    val status: TranscriptStatus,
    val editedAt: String? = null,
    val provenance: List<ProvenanceEntry>,
    val retentionClass: RetentionClass,
    val version: Long,
    val deletedAt: String? = null,
)

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Double? = null,
)

enum class TranscriptStatus { RAW, EDITED, SUPERSEDED }
