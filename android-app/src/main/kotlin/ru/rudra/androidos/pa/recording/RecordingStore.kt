package ru.rudra.androidos.pa.recording

import java.io.File

data class RecordingItem(
    val id: String,
    val path: String,
    val capturedLabel: String,
    val sizeBytes: Long,
)

/**
 * Lists finished m4a recordings produced by [RecordingService]. Pure data
 * layer; UI renders it through the shared contract slot agreed with the
 * Studio session.
 */
object RecordingStore {

    fun list(filesDir: File, externalRecordingsDir: File?): List<RecordingItem> =
        sequenceOf(externalRecordingsDir, filesDir)
            .filterNotNull()
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension == "m4a" }?.asSequence() ?: emptySequence() }
            .sortedByDescending { it.lastModified() }
            .map { f ->
                RecordingItem(
                    id = f.nameWithoutExtension,
                    path = f.absolutePath,
                    capturedLabel = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(f.lastModified())),
                    sizeBytes = f.length(),
                )
            }
            .toList()
}
