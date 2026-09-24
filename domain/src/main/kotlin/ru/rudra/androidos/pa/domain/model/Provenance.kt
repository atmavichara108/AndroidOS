package ru.rudra.androidos.pa.domain.model

data class ProvenanceEntry(
    val source: ProvenanceSource,
    val actor: String,
    val at: String,
    val detail: String? = null,
)

enum class ProvenanceSource { CAPTURE, STT_ENGINE, HUMAN_EDIT, EXTRACTION_ENGINE, IMPORT, SYNC_PEER }
