package com.middleproject.tripcopilot.ui

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary
import com.middleproject.tripcopilot.alarm.LocalAlarm
import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.data.DeviceRefreshBackend
import com.middleproject.tripcopilot.data.DeviceRepository
import com.middleproject.tripcopilot.data.InMemoryDeviceTokenStore
import com.middleproject.tripcopilot.domain.AlarmCancelBoundary
import com.middleproject.tripcopilot.domain.BackendDisconnectClient
import com.middleproject.tripcopilot.domain.DeviceDisconnectCoordinator
import com.middleproject.tripcopilot.domain.FcmLocalStateStore
import com.middleproject.tripcopilot.domain.InstallationIdStore
import com.middleproject.tripcopilot.fcm.FcmTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repeated pair taps while exchange is pending submit only once`() = runTest(dispatcher.scheduler) {
        val backend = CountingBackend()
        val viewModel = CompanionViewModel(repository(backend))
        advanceUntilIdle()

        viewModel.submitPairingCode("ABC12-DEF34", "Phone")
        viewModel.submitPairingCode("ABC12-DEF34", "Phone")
        advanceUntilIdle()

        assertEquals(1, backend.exchangeCalls)
    }

    @Test
    fun `nearby transport result updates transport panel without replacing paired data`() = runTest(dispatcher.scheduler) {
        val backend = CountingBackend()
        val viewModel = CompanionViewModel(repository(backend))
        advanceUntilIdle()

        viewModel.findNearbyBusStops(37.5665, 126.9780)
        advanceUntilIdle()

        val state = viewModel.state.value as CompanionUiState.Paired
        assertEquals(37.5665, backend.lastLatitude!!, 0.0001)
        assertEquals(126.9780, backend.lastLongitude!!, 0.0001)
        assertTrue(state.transport.result?.success == true)
        assertEquals("서울역버스환승센터", state.transport.result?.items?.single()?.fields?.get("nodeName"))
    }

    private fun repository(backend: CountingBackend): DeviceRepository {
        val tokenStore = InMemoryDeviceTokenStore()
        val metadata = object : AlarmMetadataStore {
            override fun upsert(alarm: LocalAlarm) = Unit
            override fun findByReminderId(reminderId: String): LocalAlarm? = null
            override fun remove(reminderId: String) = Unit
            override fun all(): List<LocalAlarm> = emptyList()
        }
        val scheduler = object : AlarmSchedulerBoundary, AlarmCancelBoundary {
            override fun schedule(alarm: LocalAlarm) = Unit
            override fun cancel(reminderId: String) = Unit
            override fun cancelAll(reminderIds: Collection<String>) = Unit
            override fun canScheduleExact(): Boolean = true
        }
        val fcmState = object : FcmLocalStateStore {
            override fun isAssociated(): Boolean = false
            override fun markAssociated() = Unit
            override fun clearLocalAssociation() = Unit
        }
        val disconnect = DeviceDisconnectCoordinator(
            backend = object : BackendDisconnectClient {
                override suspend fun disconnectDevice() = Unit
                override suspend fun deleteFcmRegistration() = Unit
            },
            tokenStore = tokenStore,
            fcmLocalState = fcmState,
            metadata = metadata,
            alarms = scheduler,
        )
        return DeviceRepository(
            api = backend,
            tokenStore = tokenStore,
            installationIdStore = object : InstallationIdStore {
                override fun getOrCreate(): String = "installation-1"
            },
            metadata = metadata,
            scheduler = scheduler,
            disconnectCoordinator = disconnect,
            fcmTokenProvider = object : FcmTokenProvider {
                override suspend fun currentToken(): String? = null
            },
            fcmLocalState = fcmState,
            ioDispatcher = dispatcher,
            nowMillis = { 1_000L },
        )
    }

    private class CountingBackend : DeviceRefreshBackend {
        var exchangeCalls = 0
        var lastLatitude: Double? = null
        var lastLongitude: Double? = null

        override fun hasValidCredential(): Boolean = true

        override fun exchange(pairingCode: String, installationId: String, label: String): DeviceApiClient.ExchangeResult {
            exchangeCalls += 1
            return DeviceApiClient.ExchangeResult("token", "device", Long.MAX_VALUE)
        }

        override fun trips(): List<DeviceApiClient.TripView> = emptyList()
        override fun reminders(): List<DeviceApiClient.ReminderView> = emptyList()
        override fun delivery(reminderId: String): List<DeviceApiClient.DeliveryView> = emptyList()
        override fun cancelTrip(tripId: String, expectedVersion: Long) = Unit
        override fun cancelReminder(reminderId: String, expectedVersion: Long) = Unit
        override fun ackReminder(reminderId: String, expectedVersion: Long) = Unit
        override fun registerFcmToken(registrationToken: String) = Unit
        override fun deleteFcmToken() = Unit

        override fun nearbyBusStops(latitude: Double, longitude: Double): DeviceApiClient.TransportResult {
            lastLatitude = latitude
            lastLongitude = longitude
            return DeviceApiClient.TransportResult(
                success = true,
                empty = false,
                retryable = false,
                failureKind = null,
                items = listOf(DeviceApiClient.TransportItem(mapOf("nodeName" to "서울역버스환승센터"))),
                errorMessage = null,
            )
        }
    }
}
