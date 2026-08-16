package com.middleproject.tripcopilot

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary
import com.middleproject.tripcopilot.alarm.LocalAlarm
import com.middleproject.tripcopilot.data.DeviceRepository
import com.middleproject.tripcopilot.data.FakeDeviceRefreshBackend
import com.middleproject.tripcopilot.data.InMemoryDeviceTokenStore
import com.middleproject.tripcopilot.data.InMemoryInstallationIdStore
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
 * Contract of the production [DeviceDisconnectCoordinator]: the server disconnect is
 * attempted first, FCM deregistration is best-effort while the credential still exists,
 * and regardless of any server exception the local credential, local FCM state, and
 * every stored local alarm are always cleared. The server result is surfaced as a
 * boolean without re-authenticating locally.
 */
class DeviceRepositoryDisconnectTest {

    private lateinit var tokenStore: DeviceTokenStore
    private lateinit var metadata: InMemoryAlarmMetadataStore
    private lateinit var scheduler: RecordingAlarmScheduler
    private lateinit var fcmLocalState: InMemoryFcmLocalStateStore

    @Before
    fun setUp() {
        tokenStore = InMemoryDeviceTokenStore()
        tokenStore.save(DeviceCredential("credential-token", expiresAtEpochMillis = 2_000L))
        metadata = InMemoryAlarmMetadataStore()
        scheduler = RecordingAlarmScheduler(metadata)
        fcmLocalState = InMemoryFcmLocalStateStore()
    }

    private fun coordinator(backend: BackendDisconnectClient): DeviceDisconnectCoordinator =
        DeviceDisconnectCoordinator(
            backend = backend,
            tokenStore = tokenStore,
            fcmLocalState = fcmLocalState,
            metadata = metadata,
            alarms = scheduler,
        )

    @Test
    fun `server disconnect failure still clears token store fcm state and local alarms`() {
        metadata.upsert(alarm("r-1"))
        metadata.upsert(alarm("r-2"))

        val serverOk = runBlocking {
            coordinator(FailingBackend()).disconnect()
        }

        assertFalse(serverOk)
        assertEquals(null, tokenStore.load())
        assertEquals(emptyList<LocalAlarm>(), metadata.all())
        assertEquals(setOf("r-1", "r-2"), scheduler.cancelled)
        assertTrue(fcmLocalState.cleared)
    }

    @Test
    fun `successful server disconnect and fcm deregistration clear everything and return true`() {
        metadata.upsert(alarm("r-1"))

        val serverOk = runBlocking {
            coordinator(RecordingBackend()).disconnect()
        }

        assertTrue(serverOk)
        assertEquals(listOf("disconnect", "fcm-delete"), RecordingBackend.global.calls)
        assertEquals(null, tokenStore.load())
        assertEquals(emptyList<LocalAlarm>(), metadata.all())
        assertEquals(setOf("r-1"), scheduler.cancelled)
        assertTrue(fcmLocalState.cleared)
    }

    @Test
    fun `fcm deregistration failure does not prevent local cleanup`() {
        metadata.upsert(alarm("r-1"))

        val serverOk = runBlocking {
            coordinator(FailingFcmDeleteBackend()).disconnect()
        }

        assertTrue(serverOk)
        assertEquals(null, tokenStore.load())
        assertEquals(emptyList<LocalAlarm>(), metadata.all())
        assertEquals(setOf("r-1"), scheduler.cancelled)
        assertTrue(fcmLocalState.cleared)
    }

    @Test
    fun `local cleanup runs even when no alarms are stored`() {
        val serverOk = runBlocking {
            coordinator(RecordingBackend()).disconnect()
        }

        assertTrue(serverOk)
        assertEquals(null, tokenStore.load())
        assertTrue(fcmLocalState.cleared)
        assertEquals(emptySet<String>(), scheduler.cancelled)
    }

    @Test
    fun `deleteFcmToken clears the local fcm association after server deregistration`() {
        fcmLocalState.associated = true
        val repository = DeviceRepository(
            api = FakeDeviceRefreshBackend(),
            tokenStore = tokenStore,
            installationIdStore = InMemoryInstallationIdStore(),
            metadata = metadata,
            scheduler = object : AlarmSchedulerBoundary {
                override fun schedule(alarm: LocalAlarm) = Unit
                override fun cancel(reminderId: String) = Unit
                override fun cancelAll(reminderIds: Collection<String>) = Unit
                override fun canScheduleExact(): Boolean = true
            },
            disconnectCoordinator = coordinator(RecordingBackend()),
            fcmTokenProvider = object : FcmTokenProvider {
                override suspend fun currentToken(): String? = null
            },
            fcmLocalState = fcmLocalState,
        )

        runBlocking { repository.deleteFcmToken() }

        assertFalse(fcmLocalState.associated)
        assertTrue(fcmLocalState.deleteCalled)
    }

    @Test
    fun `clearLocalOnly clears the fcm association and alarms without touching the server`() {
        fcmLocalState.associated = true
        metadata.upsert(alarm("r-1"))
        val backend = FakeDeviceRefreshBackend()
        val cancels = mutableSetOf<String>()
        val repository = DeviceRepository(
            api = backend,
            tokenStore = tokenStore,
            installationIdStore = InMemoryInstallationIdStore(),
            metadata = metadata,
            scheduler = object : AlarmSchedulerBoundary {
                override fun schedule(alarm: LocalAlarm) = Unit
                override fun cancel(reminderId: String) {
                    cancels += reminderId
                }

                override fun cancelAll(reminderIds: Collection<String>) {
                    reminderIds.forEach { cancel(it) }
                }

                override fun canScheduleExact(): Boolean = true
            },
            disconnectCoordinator = coordinator(RecordingBackend()),
            fcmTokenProvider = object : FcmTokenProvider {
                override suspend fun currentToken(): String? = null
            },
            fcmLocalState = fcmLocalState,
        )

        repository.clearLocalOnly()

        assertEquals(null, tokenStore.load())
        assertFalse(fcmLocalState.associated)
        assertEquals(setOf("r-1"), cancels)
        assertFalse("clearLocalOnly must not call the backend", backend.deleteFcmTokenCalled)
    }

    private fun alarm(reminderId: String) = LocalAlarm(
        reminderId = reminderId,
        triggerAtEpochMillis = 9_000L,
        title = "t",
        message = "m",
    )
}

class FailingBackend : BackendDisconnectClient {
    override suspend fun disconnectDevice(): Nothing = throw IllegalStateException("server unreachable")
    override suspend fun deleteFcmRegistration(): Nothing = throw IllegalStateException("server unreachable")
}

class FailingFcmDeleteBackend : BackendDisconnectClient {
    override suspend fun disconnectDevice() = Unit
    override suspend fun deleteFcmRegistration(): Nothing = throw IllegalStateException("server unreachable")
}

class RecordingBackend : BackendDisconnectClient {
    override suspend fun disconnectDevice() {
        global.calls += "disconnect"
    }

    override suspend fun deleteFcmRegistration() {
        global.calls += "fcm-delete"
    }

    companion object {
        val global = RecordingCalls()
    }
}

class RecordingCalls {
    val calls = mutableListOf<String>()
}

class InMemoryAlarmMetadataStore : AlarmMetadataStore {
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

class RecordingAlarmScheduler(private val metadata: AlarmMetadataStore) : AlarmCancelBoundary {
    val cancelled = mutableSetOf<String>()

    override fun cancel(reminderId: String) {
        cancelled += reminderId
        metadata.remove(reminderId)
    }

    override fun cancelAll(reminderIds: Collection<String>) {
        reminderIds.forEach { cancel(it) }
    }
}

class InMemoryFcmLocalStateStore : FcmLocalStateStore {
    var cleared = false
    var deleteCalled = false
    var associated = false
    override fun isAssociated(): Boolean = associated
    override fun markAssociated() {
        associated = true
    }

    override fun clearLocalAssociation() {
        cleared = true
        deleteCalled = true
        associated = false
    }
}
