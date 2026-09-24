package ru.rudra.androidos.pa

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.InboxKind
import ru.rudra.androidos.pa.domain.model.InboxState
import ru.rudra.androidos.pa.domain.model.RetentionClass

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
                InboxScreen(db, store, deviceId)
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
private fun InboxScreen(
    db: PaDatabase,
    store: RoomLocalStore,
    deviceId: String,
) {
    var text by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(emptyList<InboxItemRow>()) }

    fun reload() {
        Thread { runCatching { items = db.inboxDao().all() } }.start()
    }

    Column(Modifier.padding(16.dp)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New note") },
        )
        Button(
            onClick = {
                val t = text
                if (t.isNotBlank()) {
                    text = ""
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
                                    body = t,
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
                                    patch = mapOf("body" to t),
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
                        runCatching { items = db.inboxDao().all() }
                    }.start()
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Capture")
        }
        items.forEach { row ->
            Row(Modifier.padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${row.capturedAt.drop(11).take(8)} ${row.body.orEmpty()}")
                    ReminderTextRow(db, row.id)
                }
                TextButton({ approve(db, store, deviceId, row.id, "TASK") }) { Text("→Task") }
                TextButton({ approve(db, store, deviceId, row.id, "EVENT") }) { Text("→Event") }
            }
        }
    }
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
    }.start()
}
