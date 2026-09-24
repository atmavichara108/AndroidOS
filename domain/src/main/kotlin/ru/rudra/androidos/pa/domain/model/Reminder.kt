package ru.rudra.androidos.pa.domain.model

data class Reminder(
    val id: String,
    val targetId: String,
    val schedule: ReminderSchedule,
    val timezone: String,
    val state: ReminderState,
    val notificationPolicy: NotificationPolicy,
    val provenance: List<ProvenanceEntry>,
    val version: Long,
)

data class ReminderSchedule(
    val triggerAt: String,
    val repeat: String? = null,
)

enum class ReminderState { ACTIVE, DONE, CANCELLED }

data class NotificationPolicy(
    val channel: String,
    val exactAlarmAllowed: Boolean,
)
