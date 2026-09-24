package ru.rudra.androidos.pa.data

import java.util.UUID
import ru.rudra.androidos.pa.domain.model.ProvenanceEntry
import ru.rudra.androidos.pa.domain.model.ProvenanceSource
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.model.Transcript
import ru.rudra.androidos.pa.domain.model.TranscriptStatus
import ru.rudra.androidos.pa.domain.port.Transcriber
import ru.rudra.androidos.pa.domain.port.TranscriberResult

/**
 * Stub Transcriber for the first vertical slice: manual text entry substitutes
 * STT output. Real STT (Vosk / sherpa-onnx / whisper.cpp) lands after the
 * agreed corpus benchmark per ADR. [проверить]
 */
class ManualTranscriber(private val deviceId: String) : Transcriber {

    override fun engineId(): String = "manual-stub"

    override fun transcribe(audioRef: String, hints: List<String>): TranscriberResult {
        // Inputs arrive from the UI edit-text flow; audioRef unused here.
        val now = java.time.Instant.now().toString()
        val transcript = Transcript(
            id = UUID.randomUUID().toString(),
            inboxItemId = audioRef,
            text = "",
            languageHints = hints,
            segments = emptyList(),
            engineId = engineId(),
            modelId = "manual",
            status = TranscriptStatus.RAW,
            editedAt = null,
            provenance = listOf(
                ProvenanceEntry(
                    source = ProvenanceSource.HUMAN_EDIT,
                    actor = deviceId,
                    at = now,
                )
            ),
            retentionClass = RetentionClass.TEMPORARY_TRANSCRIPT,
            version = 1,
        )
        return TranscriberResult.Error("manual stub: transcribe() unused; text set by UI")
    }
}
