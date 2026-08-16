package com.middleproject.tripcopilot.fcm

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.LocalAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the production [handleFcmReminderMessage] dispatch over
 * [ReminderAlarmCoordinator] and the [FcmReminderPayload] parser — the same decision
 * branch [TripCopilotMessagingService.onMessageReceived] uses, so parser and dispatch
 * cannot diverge. An active known status with a bounded future alarmTime schedules
 * exactly one alarm; each explicit supported terminal status
 * (CANCELLED/ACKNOWLEDGED/DELIVERY_FAILED/SCHEDULE_FAILED) cancels the reminder's
 * alarm immediately even without alarmTime, which never blocks the cancellation; and
 * unknown, malformed, or oversized payloads are rejected without side effects.
 * Nothing is logged or printed.
 */
class FcmAlarmCoordinationTest {

    private val reminderId = "8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a"
    private val now = 1_000L

    private fun payload(status: String, alarmTime: Long) = mapOf(
        "reminderId" to reminderId,
        "status" to status,
        "alarmTime" to alarmTime.toString(),
        "title" to "Flight",
        "message" to "Gate changed",
    )

    private class InMemoryAlarmMetadataStore : AlarmMetadataStore {
        private val alarms = mutableMapOf<String, LocalAlarm>()

        override fun upsert(alarm: LocalAlarm) {
            alarms[alarm.reminderId] = alarm
        }

        override fun findByReminderId(reminderId: String): LocalAlarm? = alarms[reminderId]

        override fun remove(reminderId: String) {
            alarms.remove(reminderId)
        }

        override fun all(): List<LocalAlarm> = alarms.values.toList()
    }

    private class RecordingScheduler(private val metadata: AlarmMetadataStore) : com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary {
        val scheduled = mutableMapOf<String, LocalAlarm>()
        val cancelled = mutableSetOf<String>()
        var scheduleCalls = 0

        override fun schedule(alarm: LocalAlarm) {
            metadata.upsert(alarm)
            scheduled[alarm.reminderId] = alarm
            scheduleCalls += 1
        }

        override fun cancel(reminderId: String) {
            metadata.remove(reminderId)
            cancelled += reminderId
        }

        override fun cancelAll(reminderIds: Collection<String>) {
            reminderIds.forEach { cancel(it) }
        }

        override fun canScheduleExact(): Boolean = true
    }

    private fun dispatch(rawData: Map<String, String>?, metadata: InMemoryAlarmMetadataStore, scheduler: RecordingScheduler) {
        handleFcmReminderMessage(rawData, now, ReminderAlarmCoordinator(scheduler))
    }

    private fun scheduleActive(metadata: InMemoryAlarmMetadataStore, scheduler: RecordingScheduler) {
        dispatch(payload("SCHEDULED", alarmTime = 2_000L), metadata, scheduler)
        assertTrue(scheduler.scheduled.containsKey(reminderId))
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun `terminal fcm message without alarmTime cancels the existing alarm`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        dispatch(mapOf("reminderId" to reminderId, "status" to "CANCELLED"), metadata, scheduler)

        assertTrue(scheduler.cancelled.contains(reminderId))
        assertNull(metadata.findByReminderId(reminderId))
        assertEquals("no alarm may be scheduled by a terminal message", 1, scheduler.scheduleCalls)
    }

    @Test
    fun `all four explicit terminal statuses cancel the existing alarm`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        for (status in listOf("CANCELLED", "ACKNOWLEDGED", "DELIVERY_FAILED", "SCHEDULE_FAILED")) {
            dispatch(mapOf("reminderId" to reminderId, "status" to status), metadata, scheduler)
            assertTrue("$status must cancel the alarm", scheduler.cancelled.contains(reminderId))
            assertNull(metadata.findByReminderId(reminderId))
        }
        assertEquals(1, scheduler.scheduleCalls)
        assertEquals(setOf(reminderId), scheduler.cancelled)
    }

    @Test
    fun `present alarmTime never blocks a valid terminal cancellation`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        dispatch(payload("CANCELLED", alarmTime = 999_999_999_999L), metadata, scheduler)

        assertTrue(scheduler.cancelled.contains(reminderId))
        assertNull(metadata.findByReminderId(reminderId))
        assertEquals(1, scheduler.scheduleCalls)
    }

    @Test
    fun `unknown status like FAILED causes no schedule and no cancel`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        dispatch(payload("FAILED", alarmTime = 2_000L), metadata, scheduler)

        assertTrue("unknown status must not cancel anything", scheduler.cancelled.isEmpty())
        assertEquals("unknown status must not schedule anything", 1, scheduler.scheduleCalls)
        assertTrue("existing alarm must survive an unknown status", metadata.findByReminderId(reminderId) != null)
    }

    @Test
    fun `malformed or oversized id and status cause no side effect`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        val malformed = listOf(
            mapOf("status" to "CANCELLED"), // missing reminderId
            payload("CANCELLED", alarmTime = 2_000L) + ("reminderId" to "not-a-uuid"),
            payload("CANCELLED", alarmTime = 2_000L) + ("reminderId" to "8D0FF0E0-0F3F-4B1A-9F6D-9E9B1C1A1A1A"),
            payload("CANCELLED", alarmTime = 2_000L) + ("reminderId" to "x".repeat(301)),
            payload("CANCELLED", alarmTime = 2_000L) + ("status" to "z".repeat(301)),
            mapOf("reminderId" to reminderId, "alarmTime" to "2_000"), // missing status
            payload("SCHEDULED", alarmTime = 2_000L) + ("reminderId" to "x".repeat(301)),
        )
        for (raw in malformed) {
            dispatch(raw, metadata, scheduler)
        }

        assertTrue("malformed payloads must not cancel anything", scheduler.cancelled.isEmpty())
        assertEquals("malformed payloads must not schedule anything", 1, scheduler.scheduleCalls)
        assertTrue("existing alarm must survive rejected payloads", metadata.findByReminderId(reminderId) != null)
    }

    @Test
    fun `active future update schedules exactly one alarm`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)

        dispatch(payload("SCHEDULED", alarmTime = 2_000L), metadata, scheduler)
        dispatch(payload("SCHEDULED", alarmTime = 3_000L), metadata, scheduler)

        assertEquals(2, scheduler.scheduleCalls)
        assertEquals(reminderId, scheduler.scheduled.keys.single())
        assertEquals(3_000L, scheduler.scheduled.getValue(reminderId).triggerAtEpochMillis)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun `active past or malformed update does nothing`() {
        val metadata = InMemoryAlarmMetadataStore()
        val scheduler = RecordingScheduler(metadata)
        scheduleActive(metadata, scheduler)

        dispatch(payload("SCHEDULED", alarmTime = 999L), metadata, scheduler) // past
        dispatch(payload("SCHEDULED", alarmTime = 1_000L), metadata, scheduler) // at-now
        dispatch(payload("SCHEDULED", alarmTime = 2_000L) + ("alarmTime" to "not-a-number"), metadata, scheduler) // malformed
        dispatch(mapOf("reminderId" to reminderId, "status" to "SCHEDULED"), metadata, scheduler) // missing alarmTime

        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(setOf(reminderId), scheduler.scheduled.keys)
        assertEquals(2_000L, scheduler.scheduled.getValue(reminderId).triggerAtEpochMillis)
        assertTrue(metadata.findByReminderId(reminderId) != null)
    }
}
