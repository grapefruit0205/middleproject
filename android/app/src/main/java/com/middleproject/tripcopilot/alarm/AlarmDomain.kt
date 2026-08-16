package com.middleproject.tripcopilot.alarm

import java.util.UUID

/** A durable local alarm for one reminder, keyed by the reminder UUID. */
data class LocalAlarm(
    val reminderId: String,
    val triggerAtEpochMillis: Long,
    val title: String,
    val message: String,
)

/**
 * Scheduling boundary used by the repository and disconnect cleanup. Implemented by
 * [AlarmScheduler]; kept separate so JVM tests can fake scheduling deterministically.
 */
interface AlarmSchedulerBoundary {
    fun schedule(alarm: LocalAlarm)

    fun cancel(reminderId: String)

    fun cancelAll(reminderIds: Collection<String>)

    fun canScheduleExact(): Boolean
}

/**
 * Durable local alarm metadata keyed by reminder UUID. Repeated sync/FCM for the same
 * reminder replaces one alarm; cancel/ACK removes it. Storage is injected so JVM tests
 * are deterministic.
 */
interface AlarmMetadataStore {

    /** Replaces any existing alarm for this reminder with the new one. */
    fun upsert(alarm: LocalAlarm)

    fun findByReminderId(reminderId: String): LocalAlarm?

    fun remove(reminderId: String)

    fun all(): List<LocalAlarm>
}

/**
 * Pure scheduling policy: eligibility for alarm scheduling and whether an existing
 * alarm is reschedulable after boot/time changes. All decisions are derived from
 * reminder status and timestamps so JVM tests are deterministic.
 */
object AlarmPolicy {

    private val SCHEDULABLE_STATUSES = setOf(
        "SCHEDULE_PENDING",
        "SCHEDULED",
        "DISPATCHED",
        "DELIVERED",
        "RETRYING",
    )

    /** Explicit statuses whose reminder is definitively finished and must never produce
     *  an alarm: cancelled, acknowledged, or failed to schedule/deliver. */
    private val TERMINAL_STATUSES = setOf(
        "CANCELLED",
        "ACKNOWLEDGED",
        "DELIVERY_FAILED",
        "SCHEDULE_FAILED",
    )

    /**
     * Whether the status is one of the explicit terminal statuses that cancels the
     * reminder's alarm. Any other status — including unknown or misspelled ones — is
     * not treated as terminal, so a typo can never cancel an alarm.
     */
    fun isTerminal(status: String): Boolean = status in TERMINAL_STATUSES

    /**
     * An alarm is schedulable only when the status is one of the explicit active
     * statuses and the alarm time is in the future. Terminal statuses and any
     * unknown or misspelled status are never schedulable.
     */
    fun shouldSchedule(status: String, alarmTimeEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        if (status !in SCHEDULABLE_STATUSES) return false
        val alarmTime = alarmTimeEpochMillis ?: return false
        return alarmTime > nowEpochMillis
    }
}

/** Stable PendingIntent identity: one alarm per reminder UUID. */
object AlarmIdentity {
    fun requestCode(reminderId: String): Int =
        UUID.fromString(reminderId).hashCode() and 0x7fffffff
}
