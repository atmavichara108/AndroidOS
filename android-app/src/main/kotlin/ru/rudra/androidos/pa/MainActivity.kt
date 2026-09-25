package ru.rudra.androidos.pa

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import ru.rudra.androidos.pa.domain.statemachine.CaptureState
import ru.rudra.androidos.pa.recording.RecordingBus
import ru.rudra.androidos.pa.recording.RecordingCommands
import ru.rudra.androidos.pa.recording.RecordingStore
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.InboxKind
import ru.rudra.androidos.pa.domain.model.InboxState
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.ui.InboxScreen
import ru.rudra.androidos.pa.ui.UiInboxAction
import ru.rudra.androidos.pa.ui.UiInboxItem
import ru.rudra.androidos.pa.ui.UiInboxState
import ru.rudra.androidos.pa.ui.UiRecording

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

    fun toUiState(rows: List<InboxItemRow>) = UiInboxState(
        items = rows.map { r ->
            UiInboxItem(
                id = r.id,
                body = r.body.orEmpty(),
                stateLabel = r.state,
                capturedLabel = r.capturedAt.drop(11).take(8),
            )
        },
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
            when (action) {
                is UiInboxAction.Capture -> capture(db, store, deviceId, action.text) { reload() }
                is UiInboxAction.ApproveTask -> approve(db, store, deviceId, action.id, "TASK") { reload() }
                is UiInboxAction.ApproveEvent -> approve(db, store, deviceId, action.id, "EVENT") { reload() }
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

private fun approve(
    db: PaDatabase,
    store: RoomLocalStore,
    deviceId: String,
    inboxItemId: String,
    kind: String,
    onDone: () -> Unit,
) {
    Thread {
        val now = Instant.now().toString()
        val entityId = UUID.randomUUID().toString()
        val item = db.inboxDao().byId(inboxItemId)
        val attrsJson = org.json.JSONObject().put("title", item?.body.orEmpty()).toString()
        val triggerAt = Instant.now().plusSeconds(60 * 60).toString()
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
                    id = UUID.randomUUID().toString(),
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
                    patch = mapOf("kind" to kind, "title" to item?.body.orEmpty()),
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
