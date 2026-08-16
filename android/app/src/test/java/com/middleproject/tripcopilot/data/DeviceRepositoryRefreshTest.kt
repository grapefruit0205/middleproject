package com.middleproject.tripcopilot.data

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary
import com.middleproject.tripcopilot.alarm.LocalAlarm
import com.middleproject.tripcopilot.domain.AlarmCancelBoundary
import com.middleproject.tripcopilot.domain.BackendDisconnectClient
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceDisconnectCoordinator
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import com.middleproject.tripcopilot.domain.FcmLocalStateStore
import com.middleproject.tripcopilot.fcm.FcmTokenProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract of the refresh pipeline: alarms absent from the latest server reminder set
 * are cancelled, terminal/past reminders are not scheduled, future active reminders
 * are scheduled, and the SyncResult carries per-reminder delivery plus the degraded
 * alarm flag. A non-auth delivery failure becomes an empty list.
 */
class DeviceRepositoryRefreshTest {

    private lateinit var tokenStore: DeviceTokenStore
    private lateinit var metadata: InMemoryAlarmMetadataStore
    private lateinit var scheduler: RecordingScheduler
    private lateinit var fcmLocalState: InMemoryFcmLocalStateStore

    @Before
    fun setUp() {
        tokenStore = InMemoryDeviceTokenStore()
        tokenStore.save(DeviceCredential("credential-token", expiresAtEpochMillis = 2_000L))
        metadata = InMemoryAlarmMetadataStore()
        scheduler = RecordingScheduler(metadata)
        fcmLocalState = InMemoryFcmLocalStateStore()
    }

    @Test
    fun `refresh cancels stale absent alarms schedules future active and maps delivery and degraded`() {
        metadata.upsert(LocalAlarm("stale-id", triggerAtEpochMillis = 9_000L, title = "t", message = "m"))
        scheduler.degraded = true
        val backend = FakeRefreshBackend(
            reminders = listOf(reminder("r-future", "SCHEDULED", alarmTime = 9_000L)),
        )
        val repository = repository(backend)

        val result = runBlocking { repository.refresh() }

        assertTrue(scheduler.cancelled.contains("stale-id"))
        assertEquals(null, metadata.findByReminderId("stale-id"))
        assertTrue(scheduler.scheduled.containsKey("r-future"))
        assertEquals(listOf(backend.deliveryView), result.delivery["r-future"])
        assertTrue(result.degradedAlarm)
        assertEquals(listOf("r-future"), result.reminders.map { it.id })
    }

    @Test
    fun `refresh cancels terminal and past reminders and delivery failure becomes empty list`() {
        val backend = FakeRefreshBackend(
            reminders = listOf(
                reminder("r-terminal", "CANCELLED", alarmTime = 9_000L),
                reminder("r-past", "SCHEDULED", alarmTime = 1_000L),
                reminder("r-ok", "SCHEDULED", alarmTime = 9_000L),
            ),
            failDeliveryFor = setOf("r-ok"),
        )
        val repository = repository(backend)

        val result = runBlocking { repository.refresh() }

        assertTrue(scheduler.cancelled.contains("r-terminal"))
        assertTrue(scheduler.cancelled.contains("r-past"))
        assertTrue(scheduler.scheduled.containsKey("r-ok"))
        assertEquals(emptyList<DeviceApiClient.DeliveryView>(), result.delivery["r-ok"])
        assertFalse(result.degradedAlarm)
    }

    @Test
    fun `exchange rejects a blank label before any backend call`() {
        val backend = FakeRefreshBackend()
        val repository = repository(backend)

        try {
            runBlocking { repository.exchange(pairingCode = "AAAAA-BBBBB", label = "   ") }
            throw AssertionError("blank label must be rejected by the repository")
        } catch (e: IllegalArgumentException) {
            assertEquals("Device label must be 1-200 characters", e.message)
        }
        assertFalse(backend.exchangeCalled)
    }

    @Test
    fun `exchange rejects an oversized label before any backend call`() {
        val backend = FakeRefreshBackend()
        val repository = repository(backend)

        try {
            runBlocking { repository.exchange(pairingCode = "AAAAA-BBBBB", label = "x".repeat(201)) }
            throw AssertionError("oversized label must be rejected by the repository")
        } catch (e: IllegalArgumentException) {
            assertEquals("Device label must be 1-200 characters", e.message)
        }
        assertFalse(backend.exchangeCalled)
    }

    @Test
    fun `exchange accepts a boundary label of exactly 200 characters`() {
        val backend = FakeRefreshBackend()
        val repository = repository(backend)

        runBlocking { repository.exchange(pairingCode = "AAAAA-BBBBB", label = "x".repeat(200)) }

        assertTrue(backend.exchangeCalled)
    }

    // ---- fakes ----

    private fun repository(backend: FakeRefreshBackend): DeviceRepository =
        DeviceRepository(
            api = backend,
            tokenStore = tokenStore,
            installationIdStore = InMemoryInstallationIdStore(),
            metadata = metadata,
            scheduler = scheduler,
            disconnectCoordinator = DeviceDisconnectCoordinator(
                backend = object : BackendDisconnectClient {
                    override suspend fun disconnectDevice() = Unit
                    override suspend fun deleteFcmRegistration() = Unit
                },
                tokenStore = tokenStore,
                fcmLocalState = fcmLocalState,
                metadata = metadata,
                alarms = scheduler,
            ),
            fcmTokenProvider = object : FcmTokenProvider {
                override suspend fun currentToken(): String? = null
            },
            fcmLocalState = fcmLocalState,
            nowMillis = { 5_000L },
        )

    @Test
    fun `exchange validates the label and reaches the backend for a valid label`() {
        val backend = FakeRefreshBackend()
        val repository = repository(backend)

        runBlocking { repository.exchange(pairingCode = "AAAAA-BBBBB", label = "Pixel 9") }

        assertTrue(backend.exchangeCalled)
    }

    private fun reminder(id: String, status: String, alarmTime: Long?) = DeviceApiClient.ReminderView(
        id = id,
        eventId = "event-$id",
        policyId = "policy-$id",
        tripId = null,
        status = status,
        version = 1L,
        alarmTimeEpochMillis = alarmTime,
    )
}

/** Pure scheduler fake recording schedule/cancel calls and the degraded flag. */
private class RecordingScheduler(
    private val metadata: AlarmMetadataStore,
) : AlarmSchedulerBoundary, AlarmCancelBoundary {
    val scheduled = mutableMapOf<String, LocalAlarm>()
    val cancelled = mutableSetOf<String>()
    var degraded: Boolean = false

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

    override fun canScheduleExact(): Boolean = !degraded
}

/** In-memory alarm metadata store for JVM tests. */
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

private class InMemoryFcmLocalStateStore : FcmLocalStateStore {
    override fun isAssociated(): Boolean = false
    override fun markAssociated() = Unit
    override fun clearLocalAssociation() = Unit
}

private class FakeRefreshBackend(
    private val reminders: List<DeviceApiClient.ReminderView> = emptyList(),
    private val failDeliveryFor: Set<String> = emptySet(),
) : DeviceRefreshBackend {
    val deliveryView = DeviceApiClient.DeliveryView(channel = "PUSH", status = "DELIVERED", attemptedAtEpochMillis = 8_000L)
    var exchangeCalled = false

    override fun trips(): List<DeviceApiClient.TripView> = emptyList()

    override fun reminders(): List<DeviceApiClient.ReminderView> = reminders

    override fun delivery(reminderId: String): List<DeviceApiClient.DeliveryView> {
        if (reminderId in failDeliveryFor) throw DeviceApiClient.ApiException.Network("delivery unavailable")
        return listOf(deliveryView)
    }

    override fun hasValidCredential(): Boolean = true

    override fun exchange(
        pairingCode: String,
        installationId: String,
        label: String,
    ): DeviceApiClient.ExchangeResult {
        exchangeCalled = true
        return DeviceApiClient.ExchangeResult("token-1", "device-1", expiresAtEpochMillis = 2_000L)
    }

    override fun cancelTrip(tripId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun cancelReminder(reminderId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun ackReminder(reminderId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun registerFcmToken(registrationToken: String) = throw UnsupportedOperationException()

    override fun deleteFcmToken() = throw UnsupportedOperationException()
}
