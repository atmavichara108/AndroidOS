package ru.rudra.androidos.pa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inbox_items")
data class InboxItemRow(
    @PrimaryKey val id: String,
    val kind: String,
    val state: String,
    val transcriptId: String?,
    val body: String?,
    val sourceDeviceId: String,
    val capturedAt: String,
    val createdAt: String,
    val updatedAt: String,
    val retentionClass: String,
    val version: Long,
    val deletedAt: String?,
)

@Entity(tableName = "transcripts")
data class TranscriptRow(
    @PrimaryKey val id: String,
    val inboxItemId: String,
    val text: String,
    val engineId: String,
    val modelId: String,
    val status: String,
    val editedAt: String?,
    val retentionClass: String,
    val version: Long,
    val deletedAt: String?,
)

@Entity(tableName = "entities")
data class EntityRow(
    @PrimaryKey val id: String,
    val type: String,
    val schemaVersion: Int,
    val attributesJson: String,
    val status: String,
    val version: Long,
    val deletedAt: String?,
)

@Entity(tableName = "reminders")
data class ReminderRow(
    @PrimaryKey val id: String,
    val targetId: String,
    val triggerAt: String,
    val timezone: String,
    val state: String,
    val version: Long,
)

@Entity(tableName = "changes")
data class ChangeRow(
    @PrimaryKey val id: String,
    val entityId: String,
    val operation: String,
    val patchJson: String,
    val actorDeviceId: String,
    val baseVersion: Long?,
    val occurredAt: String,
    val idempotencyKey: String,
    val retentionClass: String,
)
