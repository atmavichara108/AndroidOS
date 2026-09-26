package ru.rudra.androidos.pa

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import ru.rudra.androidos.pa.data.EntityRow
import ru.rudra.androidos.pa.data.InboxItemRow
import ru.rudra.androidos.pa.data.PaDatabase
import ru.rudra.androidos.pa.data.ReminderRow
import ru.rudra.androidos.pa.data.RoomLocalStore
import ru.rudra.androidos.pa.data.SherpaTranscriber
import ru.rudra.androidos.pa.data.TranscriptRow
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.InboxKind
import ru.rudra.androidos.pa.domain.model.InboxState
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.model.Transcript
import ru.rudra.androidos.pa.domain.model.TranscriptStatus
import ru.rudra.androidos.pa.domain.port.TranscriberResult
import ru.rudra.androidos.pa.domain.statemachine.CaptureState
import ru.rudra.androidos.pa.recording.RecordingBus
import ru.rudra.androidos.pa.recording.RecordingCommands
import ru.rudra.androidos.pa.recording.RecordingStore
import ru.rudra.androidos.pa.reminder.ReminderScheduler
import ru.rudra.androidos.pa.ui.InboxScreen
import ru.rudra.androidos.pa.ui.PendingApproval
import ru.rudra.androidos.pa.ui.UiInboxAction
import ru.rudra.androidos.pa.ui.UiInboxItem
import ru.rudra.androidos.pa.ui.UiInboxState
import ru.rudra.androidos.pa.ui.UiRecording
import java.io.File
import android.os.Environment

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* потери отсутствуют: запись просто не начнётся без гранта на микрофон */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        val db = PaDatabase.get(this)
        val store = RoomLocalStore(db)
        val deviceId = UUID.randomUUID().toString()
        setContent {
            MaterialTheme {
                InboxScreenHost(db, store, deviceId)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        requestPermissions.launch(perms)
    }
}

@Composable
private fun InboxScreenHost(
    db: PaDatabase,
    store: RoomLocalStore,
    deviceId: String,
) {
    var ui by remember { mutableStateOf(UiInboxState(isLoading = true)) }
    var recordings by remember { mutableStateOf(emptyList<UiRecording>()) }
    val captureState by RecordingBus.state.collectAsState()
    val context = LocalContext.current
    val player = remember { android.media.MediaPlayer() }
    val transcriber = remember { SherpaTranscriber(context) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    fun loadRecordings() {
        Thread {
            recordings = runCatching {
                val extDir = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_RECORDINGS)
                } else {
                    null
                }
                RecordingStore.list(context.filesDir, extDir).map {
                    UiRecording(
                        path = it.path,
                        label = it.capturedLabel,
                        sizeBytes = it.sizeBytes,
                    )
                }
            }.getOrElse { emptyList() }
        }.start()
    }

    fun play(rec: UiRecording) {
        Thread {
            runCatching {
                player.reset()
                player.setDataSource(rec.path)
                player.prepare()
                player.start()
            }
        }.start()
    }

    var transcribingIds by remember { mutableStateOf(emptySet<String>()) }
    var editingIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingApproval by remember { mutableStateOf<PendingApproval?>(null) }

    fun toUiState(rows: List<InboxItemRow>) = UiInboxState(
        items = rows.map { r ->
            val transcript = r.transcriptId?.let { tid ->
                db.transcriptDao().forInboxItem(r.id).lastOrNull { it.id == tid }
                    ?: db.transcriptDao().forInboxItem(r.id).lastOrNull()
            }
            UiInboxItem(
                id = r.id,
                body = r.body.orEmpty(),
                stateLabel = r.state,
                capturedLabel = r.capturedAt.drop(11).take(8),
                kind = r.kind,
                transcriptText = transcript?.text,
                transcriptStatus = transcript?.status,
                isTranscribing = r.id in transcribingIds,
                isTranscriptEditing = r.id in editingIds,
            )
        },
        pendingApproval = pendingApproval,
    )

    fun reload() {
        loadRecordings()
        Thread {
            ui = runCatching { toUiState(db.inboxDao().all()) }
                .getOrElse { UiInboxState(error = it.message ?: "load failed") }
        }.start()
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(captureState) {
        if (captureState == CaptureState.IDLE || captureState == CaptureState.STOPPED) reload()
    }

    InboxScreen(
        state = ui,
        onAction = { action ->
            android.util.Log.d("PA_ACTION", "action=$action")
            when (action) {
                is UiInboxAction.Capture -> capture(db, store, deviceId, action.text) { reload() }
                is UiInboxAction.ApproveTask -> approve(db, store, context, deviceId, action.id, "TASK") { reload() }
                is UiInboxAction.ApproveEvent -> approve(db, store, context, deviceId, action.id, "EVENT") { reload() }
                is UiInboxAction.BeginTranscriptEdit -> {
                    editingIds = editingIds + action.id
                    reload()
                }
                is UiInboxAction.SaveTranscriptEdit -> {
                    editingIds = editingIds - action.id
                    saveTranscriptEdit(db, action.id, action.text) { reload() }
                }
                is UiInboxAction.CancelTranscriptEdit -> {
                    editingIds = editingIds - action.id
                    reload()
                }
                is UiInboxAction.Transcribe -> {
                    if (action.id in transcribingIds) return@InboxScreen
                    transcribingIds = transcribingIds + action.id
                    reload()
                    val recordingsDir = if (Build.VERSION.SDK_INT >= 31) {
                        context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                    } else {
                        null
                    } ?: context.filesDir
                    val sttDir = context.getExternalFilesDir(null)?.resolve("stt/t-one")
                    Thread {
                        val row = db.inboxDao().byId(action.id)
                        val audioPath = row?.body?.let { body ->
                            File(recordingsDir, body).takeIf { it.exists() }
                                ?: sttDir?.resolve(body)?.takeIf { it.exists() }
                        }
                        val result = when {
                            row == null -> TranscriberResult.Error("inbox item missing")
                            audioPath == null -> TranscriberResult.Error("audio file not found for ${row.body}")
                            else -> transcriber.transcribe(audioPath.absolutePath, listOf("ru"))
                        }
                        if (result is TranscriberResult.Ok) {
                            storeTranscript(db, store, action.id, result.transcript)
                        }
                        transcribingIds = transcribingIds - action.id
                        reload()
                    }.start()
                }
                is UiInboxAction.Delete -> deleteInboxItem(db, store, deviceId, context, action.id) { reload() }
                is UiInboxAction.RequestApprove -> {
                    val id = action.id
                    val kind = action.kind
                    Thread {
                        val row = db.inboxDao().byId(id)
                        val title = effectiveTitle(db, id, row)
                        pendingApproval = PendingApproval(id, kind, title)
                        reload()
                    }.start()
                }
                is UiInboxAction.ConfirmApproval -> {
                    pendingApproval = null
                    approve(db, store, context, deviceId, action.id, action.kind) { reload() }
                }
                UiInboxAction.CancelApproval -> {
                    pendingApproval = null
                    reload()
                }
            }
        },
        itemExtra = { item -> ReminderTextRow(db, item.id) },
        recordingLabel = when (captureState) {
            CaptureState.RECORDING -> "Stop"
            CaptureState.PAUSED -> "Resume"
            CaptureState.IDLE, CaptureState.STOPPED -> "Record"
        },
        onRecord = {
            val command = when (captureState) {
                CaptureState.RECORDING -> "stop"
                CaptureState.PAUSED -> "resume"
                CaptureState.IDLE, CaptureState.STOPPED -> "start"
            }
            RecordingCommands.send(context, command)
        },
        recordings = recordings,
        onPlay = { rec -> play(rec) },
        onDeleteRecording = { rec ->
            deleteRecordingFile(context, rec) { reload() }
        },
    )
}

private fun capture(
    db: PaDatabase,
    store: RoomLocalStore,
    deviceId: String,
    text: String,
    onDone: () -> Unit,
) {
    Thread {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        db.runInTransaction {
            db.inboxDao().insert(
                InboxItemRow(
                    id = id,
                    kind = InboxKind.TEXT.name,
                    state = InboxState.CAPTURED.name,
                    transcriptId = null,
                    body = text,
                    sourceDeviceId = deviceId,
                    capturedAt = now,
                    createdAt = now,
                    updatedAt = now,
                    retentionClass = RetentionClass.PERMANENT.name,
                    version = 1,
                    deletedAt = null,
                )
            )
            store.applyChange(
                Change(
                    id = UUID.randomUUID().toString(),
                    entityId = id,
                    operation = ChangeOperation.CREATE,
                    patch = mapOf("body" to text),
                    actorDeviceId = deviceId,
                    baseVersion = null,
                    occurredAt = now,
                    logicalClock = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                    provenance = emptyList(),
                    retentionClass = RetentionClass.PERMANENT,
                )
            )
        }
        onDone()
    }.start()
}

@Composable
private fun ReminderTextRow(db: PaDatabase, targetId: String) {
    var reminderText by remember(targetId) { mutableStateOf<String?>(null) }
    LaunchedEffect(targetId) {
        reminderText = withContext(Dispatchers.IO) {
            runCatching {
                val r = db.reminderDao().active().firstOrNull { it.targetId == targetId }
                r?.let { "⏰ ${it.triggerAt} (${it.timezone})" }
            }.getOrNull()
        }
    }
    reminderText?.let { Text(it) }
}

private fun storeTranscript(
    db: PaDatabase,
    store: RoomLocalStore,
    inboxItemId: String,
    transcript: Transcript,
) {
    val now = Instant.now().toString()
    db.runInTransaction {
        db.transcriptDao().insert(
            TranscriptRow(
                id = transcript.id,
                inboxItemId = inboxItemId,
                text = transcript.text,
                engineId = transcript.engineId,
                modelId = transcript.modelId,
                status = transcript.status.name,
                editedAt = null,
                retentionClass = transcript.retentionClass.name,
                version = 1,
                deletedAt = null,
            )
        )
        db.inboxDao().updateState(inboxItemId, InboxState.TRANSCRIBED.name, now)
        db.inboxDao().setTranscriptId(inboxItemId, transcript.id)
        store.applyChange(
            Change(
                id = UUID.randomUUID().toString(),
                entityId = inboxItemId,
                operation = ChangeOperation.UPDATE,
                patch = mapOf("transcriptId" to transcript.id, "state" to InboxState.TRANSCRIBED.name),
                actorDeviceId = transcript.provenance.firstOrNull()?.actor ?: "unknown",
                baseVersion = null,
                occurredAt = now,
                logicalClock = null,
                idempotencyKey = UUID.randomUUID().toString(),
                provenance = emptyList(),
                retentionClass = RetentionClass.TEMPORARY_TRANSCRIPT,
            )
        )
    }
}

private fun saveTranscriptEdit(
    db: PaDatabase,
    inboxItemId: String,
    newText: String,
    onDone: () -> Unit,
) {
    Thread {
        val now = Instant.now().toString()
        db.runInTransaction {
            val current = db.transcriptDao().forInboxItem(inboxItemId).lastOrNull()
            current?.let {
                db.transcriptDao().updateTextAndStatus(
                    id = it.id,
                    text = newText,
                    status = TranscriptStatus.EDITED.name,
                    editedAt = now,
                )
            }
        }
        onDone()
    }.start()
}

private fun deleteInboxItem(
    db: PaDatabase,
    store: RoomLocalStore,
    deviceId: String,
    context: Context,
    id: String,
    onDone: () -> Unit,
) {
    Thread {
        val now = Instant.now().toString()
        db.runInTransaction {
            val row = db.inboxDao().byId(id)
            db.inboxDao().tombstone(id, now)
            store.applyChange(
                Change(
                    id = UUID.randomUUID().toString(),
                    entityId = id,
                    operation = ChangeOperation.TOMBSTONE,
                    patch = mapOf("deletedAt" to now),
                    actorDeviceId = deviceId,
                    baseVersion = row?.version,
                    occurredAt = now,
                    logicalClock = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                    provenance = emptyList(),
                    retentionClass = RetentionClass.PERMANENT,
                )
            )
            if (row?.kind == InboxKind.AUDIO.name) {
                row.body?.let { name ->
                    val dir = if (Build.VERSION.SDK_INT >= 31) {
                        context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                    } else {
                        null
                    } ?: context.filesDir
                    val f = File(dir, name)
                    if (f.exists()) f.delete()
                }
            }
        }
        onDone()
    }.start()
}

private fun deleteRecordingFile(context: Context, rec: UiRecording, onDone: () -> Unit) {
    Thread {
        runCatching {
            val f = File(rec.path)
            if (f.exists()) f.delete()
        }
        onDone()
    }.start()
}

private fun approve(
    db: PaDatabase,
    store: RoomLocalStore,
    context: Context,
    deviceId: String,
    inboxItemId: String,
    kind: String,
    onDone: () -> Unit,
) {
    Thread {
        try {
            val now = Instant.now().toString()
            val entityId = UUID.randomUUID().toString()
            val item = db.inboxDao().byId(inboxItemId)
            val title = effectiveTitle(db, inboxItemId, item)
            val attrsJson = org.json.JSONObject().put("title", title).toString()
            val triggerAtMillis = System.currentTimeMillis() + REMINDER_DELAY_MS
            val triggerAt = Instant.ofEpochMilli(triggerAtMillis).toString()
            val reminderId = UUID.randomUUID().toString()
            db.runInTransaction {
                db.entityDao().insert(
                    EntityRow(
                        id = entityId,
                        type = kind,
                        schemaVersion = 1,
                        attributesJson = attrsJson,
                        status = "APPROVED",
                        version = 1,
                        deletedAt = null,
                    )
                )
                db.reminderDao().insert(
                    ReminderRow(
                        id = reminderId,
                        targetId = entityId,
                        triggerAt = triggerAt,
                        timezone = java.util.TimeZone.getDefault().id,
                        state = "ACTIVE",
                        version = 1,
                    )
                )
                db.inboxDao().updateState(inboxItemId, InboxState.STRUCTURED.name, now)
                store.applyChange(
                    Change(
                        id = UUID.randomUUID().toString(),
                        entityId = entityId,
                        operation = ChangeOperation.CREATE,
                        patch = mapOf("kind" to kind, "title" to title),
                        actorDeviceId = deviceId,
                        baseVersion = null,
                        occurredAt = now,
                        logicalClock = null,
                        idempotencyKey = UUID.randomUUID().toString(),
                        provenance = emptyList(),
                        retentionClass = RetentionClass.PERMANENT,
                    )
                )
            }
            ReminderScheduler.schedule(context, reminderId, entityId, triggerAtMillis)
            android.util.Log.d("PA_APPROVE", "created $kind entity=$entityId title='$title' reminder=$reminderId at $triggerAt")
        } catch (e: Exception) {
            android.util.Log.e("PA_APPROVE", "approve failed for $inboxItemId", e)
        }
        onDone()
    }.start()
}

// [проверить] фиксированный сдвиг напоминания до появления extraction дат из
// транскрипта (P2): пока «через час» — provisional placeholder.
private const val REMINDER_DELAY_MS = 60L * 60L * 1000L

/**
 * Effective human-readable title for an inbox item: edited transcript first,
 * then raw transcript (AUDIO rows), then the body (TEXT rows). AUDIO body
 * holds only the m4a file name, never a usable title.
 */
private fun effectiveTitle(db: PaDatabase, inboxItemId: String, item: InboxItemRow?): String {
    if (item?.kind == InboxKind.AUDIO.name) {
        val transcripts = runCatching { db.transcriptDao().forInboxItem(inboxItemId) }.getOrDefault(emptyList())
        transcripts.lastOrNull { it.status == TranscriptStatus.EDITED.name }?.text
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        transcripts.lastOrNull { it.status == TranscriptStatus.RAW.name }?.text
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return item.body.orEmpty().trim()
    }
    return item?.body.orEmpty().trim()
}
