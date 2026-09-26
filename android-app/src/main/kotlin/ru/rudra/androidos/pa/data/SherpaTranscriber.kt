package ru.rudra.androidos.pa.data

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig
import java.io.File
import java.util.UUID
import ru.rudra.androidos.pa.domain.model.ProvenanceEntry
import ru.rudra.androidos.pa.domain.model.ProvenanceSource
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.model.Transcript
import ru.rudra.androidos.pa.domain.model.TranscriptSegment
import ru.rudra.androidos.pa.domain.model.TranscriptStatus
import ru.rudra.androidos.pa.domain.port.Transcriber
import ru.rudra.androidos.pa.domain.port.TranscriberResult

/**
 * Real STT engine behind the [Transcriber] port: sherpa-onnx runtime with the
 * streaming T-one Russian CTC model (2025-09-08, Apache-2.0).
 *
 * Model and tokens are NOT bundled: they live in the app's external files dir
 * (files/stt/t-one/{model.onnx,tokens.txt}) and are provisioned out of band
 * (see docs/research/stt-engine-selection.md; onnx/bin artifacts are gitignored).
 * Provisional until the on-device benchmark confirms the selection.
 */
class SherpaTranscriber(private val context: Context) : Transcriber {

    private var recognizer: OnlineRecognizer? = null

    override fun engineId(): String = "sherpa-onnx-tone-ctc-ru-2025-09-08"

    private fun modelDir(): File =
        File(context.getExternalFilesDir(null), "stt/t-one")

    private fun ensureRecognizer(): OnlineRecognizer {
        recognizer?.let { return it }
        val dir = modelDir()
        val model = File(dir, "model.onnx")
        val tokens = File(dir, "tokens.txt")
        if (!model.exists() || !tokens.exists()) {
            throw IllegalStateException(
                "STT model missing in ${dir.absolutePath}; provision model.onnx + tokens.txt"
            )
        }
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                toneCtc = OnlineToneCtcModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
            ),
            decodingMethod = "greedy_search",
            enableEndpoint = false,
        )
        return OnlineRecognizer(config = config).also { recognizer = it }
    }

    override fun transcribe(audioRef: String, hints: List<String>): TranscriberResult {
        val audioFile = File(audioRef)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return TranscriberResult.Error("audio missing: $audioRef")
        }
        return try {
            val rec = ensureRecognizer()
            val decoded = M4aToPcm.decode(audioFile.absolutePath)
                ?: return TranscriberResult.Error("cannot decode audio to PCM: $audioRef")
            val samples = decoded.samples
            val sampleRate = decoded.sampleRate

            val stream = rec.createStream()
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            while (rec.isReady(stream)) rec.decode(stream)
            val text = rec.getResult(stream).text
            stream.release()

            if (text.isBlank()) {
                TranscriberResult.Error("empty transcription")
            } else {
                TranscriberResult.Ok(
                    Transcript(
                        id = UUID.randomUUID().toString(),
                        inboxItemId = audioRef,
                        text = text,
                        languageHints = hints,
                        segments = listOf(
                            TranscriptSegment(
                                startMs = 0L,
                                endMs = (samples.size.toLong() * 1000L) / sampleRate,
                                text = text,
                            )
                        ),
                        engineId = engineId(),
                        modelId = "t-one-ctc-exp-00017400",
                        status = TranscriptStatus.RAW,
                        provenance = listOf(
                            ProvenanceEntry(
                                source = ProvenanceSource.STT_ENGINE,
                                actor = engineId(),
                                at = java.time.Instant.now().toString(),
                            )
                        ),
                        retentionClass = RetentionClass.TEMPORARY_TRANSCRIPT,
                        version = 1,
                    )
                )
            }
        } catch (e: Exception) {
            TranscriberResult.Error(e.message ?: "transcription failed")
        }
    }
}
