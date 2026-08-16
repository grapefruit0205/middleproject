package com.middleproject.tripcopilot.fcm

import com.middleproject.tripcopilot.alarm.AlarmPolicy
import java.util.UUID

/**
 * Bounded, defensive parser for FCM reminder data. Unknown or malformed payloads are
 * rejected without throwing; nothing is ever logged. All fields are length-bounded
 * before use.
 */
object FcmReminderPayload {

    data class ParsedReminder(
        val reminderId: String,
        val status: String,
        val alarmTimeEpochMillis: Long,
        val title: String,
        val message: String,
    )

    private val MAX_STRING_LENGTH = 300
    private val MAX_ALARM_OFFSET_SECONDS = 90L * 24 * 60 * 60 // 90 days

    /**
     * Extracts a reminder alarm from a raw FCM data map. Returns null when the payload
     * is absent, malformed, terminal, or in the past.
     */
    fun parse(rawData: Map<String, String>?, nowEpochMillis: Long): ParsedReminder? {
        if (rawData == null || rawData.isEmpty()) return null
        val reminderId = bounded(rawData["reminderId"]) ?: return null
        if (!isUuid(reminderId)) return null
        val status = bounded(rawData["status"]) ?: return null
        val alarmTimeRaw = bounded(rawData["alarmTime"]) ?: return null
        val alarmTime = parseEpochMillis(alarmTimeRaw) ?: return null
        if (!AlarmPolicy.shouldSchedule(status, alarmTime, nowEpochMillis)) return null
        if (alarmTime > nowEpochMillis + MAX_ALARM_OFFSET_SECONDS * 1000) return null
        val title = bounded(rawData["title"]) ?: "Reminder"
        val message = bounded(rawData["message"]) ?: "You have a reminder"
        return ParsedReminder(reminderId, status, alarmTime, title, message)
    }

    /**
     * Extracts just the bounded reminder identity and status for an explicit supported
     * terminal update (CANCELLED, ACKNOWLEDGED, DELIVERY_FAILED, or SCHEDULE_FAILED),
     * so the existing alarm for that reminder can be cancelled immediately instead of
     * waiting for the next pull refresh. Cancellation is identity/status based:
     * alarmTime, title, and message are not required, and any alarmTime present does
     * not gate the cancellation. Returns null when the payload is absent, malformed,
     * oversized, or carries an unknown status. Nothing is ever logged.
     */
    fun parseTerminal(rawData: Map<String, String>?): ParsedReminder? {
        if (rawData == null || rawData.isEmpty()) return null
        val reminderId = bounded(rawData["reminderId"]) ?: return null
        if (!isUuid(reminderId)) return null
        val status = bounded(rawData["status"]) ?: return null
        if (!AlarmPolicy.isTerminal(status)) return null
        return ParsedReminder(reminderId, status, alarmTimeEpochMillis = 0L, title = "", message = "")
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value).toString() == value
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun bounded(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_STRING_LENGTH) return null
        return trimmed
    }

    private fun parseEpochMillis(raw: String): Long? {
        val millis = raw.toLongOrNull() ?: return null
        if (millis <= 0L) return null
        return millis
    }
}

/** Deduplicating scheduling: one alarm per reminder, replacing on refresh. */
class ReminderAlarmCoordinator(
    private val scheduler: com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary,
) {

    fun onReminderChanged(alarmTimeEpochMillis: Long, reminderId: String, title: String, message: String) {
        scheduler.schedule(
            com.middleproject.tripcopilot.alarm.LocalAlarm(
                reminderId = reminderId,
                triggerAtEpochMillis = alarmTimeEpochMillis,
                title = title,
                message = message,
            )
        )
    }

    fun onReminderTerminal(reminderId: String) {
        scheduler.cancel(reminderId)
    }
}

/**
 * Production FCM reminder dispatch: the single decision branch used both by
 * [TripCopilotMessagingService.onMessageReceived] and by JVM tests. An active known
 * status with a bounded future alarmTime schedules exactly one alarm; an explicit
 * supported terminal status (CANCELLED/ACKNOWLEDGED/DELIVERY_FAILED/SCHEDULE_FAILED)
 * cancels that reminder's alarm (alarmTime is not required and never blocks the
 * cancellation); everything else is rejected without side effects. Android-free so
 * the same code path is exercised deterministically in JVM tests.
 */
fun handleFcmReminderMessage(
    rawData: Map<String, String>?,
    nowEpochMillis: Long,
    coordinator: ReminderAlarmCoordinator,
) {
    val parsed = FcmReminderPayload.parse(rawData, nowEpochMillis)
    if (parsed != null) {
        coordinator.onReminderChanged(parsed.alarmTimeEpochMillis, parsed.reminderId, parsed.title, parsed.message)
        return
    }
    val terminal = FcmReminderPayload.parseTerminal(rawData) ?: return
    coordinator.onReminderTerminal(terminal.reminderId)
}
