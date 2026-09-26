package ru.rudra.androidos.pa.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Decodes an m4a/AAC audio file to a mono, 16-bit-signed PCM float array
 * using Android MediaExtractor + MediaCodec. sherpa-onnx's own WaveReader
 * only understands WAV, but our recordings are MPEG-4 (MediaRecorder output),
 * so we feed the decoded samples directly to acceptWaveform() instead.
 */
object M4aToPcm {

    data class Decoded(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    fun decode(path: String): Decoded? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)
            val trackIndex = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                16000
            }

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val pcm = java.io.ByteArrayOutputStream()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIndex)!!
                    val bytes = ByteArray(bufferInfo.size)
                    outBuf.get(bytes)
                    pcm.write(bytes)
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            val raw = pcm.toByteArray()
            val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val floats = FloatArray(shorts.remaining())
            val sb: ShortBuffer = shorts
            for (i in floats.indices) {
                floats[i] = sb.get().toFloat() / 32768.0f
            }
            Decoded(floats, sampleRate)
        } catch (e: Exception) {
            null
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }
}