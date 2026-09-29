package ru.rudra.androidos.pa.domain.port

import ru.rudra.androidos.pa.domain.model.Transcript

interface Transcriber {
    fun engineId(): String
    fun transcribe(audioRef: String, hints: List<String>): TranscriberResult
}

sealed interface TranscriberResult {
    data class Ok(val transcript: Transcript) : TranscriberResult
    data class Error(val reason: String) : TranscriberResult
}

interface ExtractionEngine {
    fun engineId(): String
    fun propose(transcript: Transcript): List<ExtractionProposal>
}

data class ExtractionProposal(
    val entityType: String,
    val attributes: Map<String, String>,
    val confidence: Double,
    val sourceTranscriptId: String,
    val fieldPaths: List<String>,
)

interface LocalStore {
    fun applyChange(change: ru.rudra.androidos.pa.domain.model.Change): Boolean
}

interface SyncTransport {
    fun send(envelope: ru.rudra.androidos.pa.domain.model.SyncEnvelope)
    fun receive(envelope: ru.rudra.androidos.pa.domain.model.SyncEnvelope)
}

/**
 * Writes a time-scoped capture into an external calendar (P2-02 personal
 * productivity contour). Implementations decide what "the calendar" is — the
 * phone's system provider on Android, a file, or nothing — so the domain stays
 * free of platform types. A sink that has no calendar to write to reports
 * [CalendarWriteResult.Unavailable] rather than failing the approval.
 */
interface CalendarSink {
    fun engineId(): String
    fun write(event: ru.rudra.androidos.pa.domain.calendar.CalendarEvent): CalendarWriteResult
}

sealed interface CalendarWriteResult {
    data class Ok(val eventId: String) : CalendarWriteResult
    data class Unavailable(val reason: String) : CalendarWriteResult
    data class Error(val reason: String) : CalendarWriteResult
}
