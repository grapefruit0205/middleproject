package com.middleproject.tripcopilot.alarm

import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract of [BootReceiver.reschedule] after BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED:
 * with a valid credential only future, active alarms are rescheduled (old registrations
 * are cancelled, past alarms dropped); with a missing or expired credential every
 * locally known scheduled alarm is cancelled and its metadata removed, because
 * AlarmManager can still hold previously scheduled alarms. Storage is injected so JVM
 * tests are deterministic; no Android runtime stubs are used.
 */
class BootReceiverRescheduleTest {

    private lateinit var tokenStore: InMemoryDeviceTokenStore
    private lateinit var metadata: InMemoryAlarmMetadataStore
    private lateinit var scheduler: RecordingScheduler
    private val now = 5_000L

    @Before
    fun setUp() {
        tokenStore = InMemoryDeviceTokenStore()
        metadata = InMemoryAlarmMetadataStore()
        scheduler = RecordingScheduler(metadata)
    }

    private fun reschedule() {
        BootReceiver().reschedule(store = tokenStore, alarms = metadata, nowEpochMillis = now, scheduler = scheduler)
    }

    private fun alarm(reminderId: String, triggerAtEpochMillis: Long) = LocalAlarm(
        reminderId = reminderId,
        triggerAtEpochMillis = triggerAtEpochMillis,
        title = "t",
        message = "m",
    )

    @Test
    fun `missing credential cancels every known alarm and removes metadata`() {
        metadata.upsert(alarm("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a", triggerAtEpochMillis = 9_000L))
        metadata.upsert(alarm("6c1e2f30-4a2b-4c3d-8e4f-5a6b7c8d9e0f", triggerAtEpochMillis = 9_000L))

        reschedule()

        assertEquals(setOf("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a", "6c1e2f30-4a2b-4c3d-8e4f-5a6b7c8d9e0f"), scheduler.cancelled)
        assertEquals(emptyList<LocalAlarm>(), metadata.all())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `expired credential cancels every known alarm and removes metadata`() {
        tokenStore.save(DeviceCredential("expired-token", expiresAtEpochMillis = now))
        metadata.upsert(alarm("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a", triggerAtEpochMillis = 9_000L))

        reschedule()

        assertEquals(setOf("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a"), scheduler.cancelled)
        assertEquals(emptyList<LocalAlarm>(), metadata.all())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `valid credential reschedules only future alarms and drops past ones`() {
        tokenStore.save(DeviceCredential("valid-token", expiresAtEpochMillis = 10_000L))
        metadata.upsert(alarm("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a", triggerAtEpochMillis = 9_000L)) // future
        metadata.upsert(alarm("6c1e2f30-4a2b-4c3d-8e4f-5a6b7c8d9e0f", triggerAtEpochMillis = 4_999L)) // past
        metadata.upsert(alarm("a1b2c3d4-5e6f-4a5b-8c7d-9e0f1a2b3c4d", triggerAtEpochMillis = now)) // at-now

        reschedule()

        // All three are cancelled (old registrations, including the future one), then
        // the future one alone is rescheduled; past and at-now alarms stay cancelled.
        assertEquals(
            setOf(
                "8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a",
                "6c1e2f30-4a2b-4c3d-8e4f-5a6b7c8d9e0f",
                "a1b2c3d4-5e6f-4a5b-8c7d-9e0f1a2b3c4d",
            ),
            scheduler.cancelled,
        )
        assertEquals(setOf("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a"), scheduler.scheduled.keys)
        assertEquals(9_000L, scheduler.scheduled["8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a"]!!.triggerAtEpochMillis)
        assertEquals(listOf("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a"), metadata.all().map { it.reminderId })
    }
}

private class InMemoryDeviceTokenStore : DeviceTokenStore {
    private var credential: DeviceCredential? = null

    override fun load(): DeviceCredential? = credential

    override fun save(credential: DeviceCredential) {
        this.credential = credential
    }

    override fun clear() {
        credential = null
    }
}

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

private class RecordingScheduler(private val metadata: AlarmMetadataStore) : AlarmSchedulerBoundary {
    val scheduled = mutableMapOf<String, LocalAlarm>()
    val cancelled = mutableSetOf<String>()

    override fun schedule(alarm: LocalAlarm) {
        metadata.upsert(alarm)
        scheduled[alarm.reminderId] = alarm
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
