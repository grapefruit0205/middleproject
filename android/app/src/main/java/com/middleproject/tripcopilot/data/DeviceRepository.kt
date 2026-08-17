package com.middleproject.tripcopilot.data

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmPolicy
import com.middleproject.tripcopilot.alarm.AlarmSchedulerBoundary
import com.middleproject.tripcopilot.alarm.LocalAlarm
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceDisconnectCoordinator
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import com.middleproject.tripcopilot.domain.FcmLocalStateStore
import com.middleproject.tripcopilot.domain.InstallationIdStore
import com.middleproject.tripcopilot.fcm.FcmBackend
import com.middleproject.tripcopilot.fcm.FcmRegistrationCoordinator
import com.middleproject.tripcopilot.fcm.FcmTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates API calls with local credential persistence, alarm scheduling, FCM
 * registration, and disconnect cleanup. Pairing uses the stable installation identity
 * (never re-generated); refresh returns a [SyncResult] with trips, reminders, per
 * reminder delivery views, and the degraded-alarm flag. Delivery failures are
 * non-fatal per reminder except Unauthorized, which propagates so the pairing expires.
 */
class DeviceRepository(
    private val api: DeviceRefreshBackend,
    private val tokenStore: DeviceTokenStore,
    private val installationIdStore: InstallationIdStore,
    private val metadata: AlarmMetadataStore,
    private val scheduler: AlarmSchedulerBoundary,
    private val disconnectCoordinator: DeviceDisconnectCoordinator,
    private val fcmTokenProvider: FcmTokenProvider,
    private val fcmLocalState: FcmLocalStateStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private val fcmRegistration = FcmRegistrationCoordinator(fcmTokenProvider, tokenStore, fcmLocalState, fcmBackend())

    suspend fun exchange(pairingCode: String, label: String) = withContext(ioDispatcher) {
        require(label.isNotBlank() && label.length <= 200) {
            "Device label must be 1-200 characters"
        }
        val result = api.exchange(pairingCode, installationIdStore.getOrCreate(), label)
        tokenStore.save(DeviceCredential(result.token, result.expiresAtEpochMillis))
        // Best-effort FCM registration after a successful exchange; pairing stays saved
        // even when FCM fails.
        fcmRegistration.registerIfNeeded()
    }

    suspend fun refresh(): SyncResult = withContext(ioDispatcher) {
        val trips = api.trips()
        val reminders = api.reminders()
        val now = nowMillis()
        val delivery = reminders.associate { reminder ->
            reminder.id to deliveryFor(reminder.id)
        }
        val degradedAlarm = !scheduler.canScheduleExact()

        val serverReminderIds = reminders.map { it.id }.toSet()
        scheduler.cancelAll(metadata.all().map { it.reminderId }.filterNot { it in serverReminderIds })

        reminders.forEach { reminder ->
            val alarmTime = reminder.alarmTimeEpochMillis
            if (AlarmPolicy.shouldSchedule(reminder.status, alarmTime, now)) {
                scheduler.schedule(
                    LocalAlarm(
                        reminderId = reminder.id,
                        triggerAtEpochMillis = alarmTime!!,
                        title = "Trip reminder",
                        message = "${reminder.status} reminder",
                    )
                )
            } else {
                scheduler.cancel(reminder.id)
            }
        }

        fcmRegistration.registerIfNeeded()

        SyncResult(trips, reminders, delivery, degradedAlarm)
    }

    suspend fun registerFcmToken(token: String) = withContext(ioDispatcher) {
        api.registerFcmToken(token)
    }

    suspend fun deleteFcmToken() = withContext(ioDispatcher) {
        api.deleteFcmToken()
        // Server-side deregistration succeeded; the local FCM association must not
        // survive, or a later refresh would skip registration of the next token.
        fcmLocalState.clearLocalAssociation()
    }

    suspend fun cancelTrip(tripId: String, expectedVersion: Long) = withContext(ioDispatcher) {
        api.cancelTrip(tripId, expectedVersion)
    }

    suspend fun cancelReminder(reminderId: String, expectedVersion: Long) = withContext(ioDispatcher) {
        api.cancelReminder(reminderId, expectedVersion)
        scheduler.cancel(reminderId)
    }

    suspend fun ackReminder(reminderId: String, expectedVersion: Long) = withContext(ioDispatcher) {
        api.ackReminder(reminderId, expectedVersion)
        scheduler.cancel(reminderId)
    }

    suspend fun realtimeSubwayArrivals(stationName: String) = withContext(ioDispatcher) {
        api.realtimeSubwayArrivals(stationName.trim())
    }

    suspend fun nearbyBusStops(latitude: Double, longitude: Double) = withContext(ioDispatcher) {
        api.nearbyBusStops(latitude, longitude)
    }

    suspend fun busStopsByLandmark(landmark: String) = withContext(ioDispatcher) {
        api.busStopsByLandmark(landmark.trim())
    }

    suspend fun busArrivals(cityCode: Int, nodeId: String) = withContext(ioDispatcher) {
        api.busArrivals(cityCode, nodeId.trim())
    }

    suspend fun expressBusArrivals(depTerminalCode: String, arrTerminalCode: String) = withContext(ioDispatcher) {
        api.expressBusArrivals(depTerminalCode.trim(), arrTerminalCode.trim())
    }

    suspend fun intercityBusSchedule(depTerminalId: String, arrTerminalId: String, date: String) =
        withContext(ioDispatcher) {
            api.intercityBusSchedule(depTerminalId.trim(), arrTerminalId.trim(), date.trim())
        }

    suspend fun transportHandoffs(): Map<String, String> = withContext(ioDispatcher) {
        api.transportHandoffs()
    }

    suspend fun transitFavorites(): List<DeviceApiClient.TransitFavorite> = withContext(ioDispatcher) {
        api.transitFavorites()
    }

    /**
     * Attempts the server disconnect through [DeviceDisconnectCoordinator], which also
     * best-effort deregisters the FCM token and always removes the local credential,
     * FCM association, and every scheduled alarm. A server failure is surfaced but
     * never leaves the device locally authenticated.
     */
    suspend fun disconnect(): Boolean = withContext(ioDispatcher) {
        disconnectCoordinator.disconnect()
    }

    fun clearLocalOnly() {
        tokenStore.clear()
        fcmLocalState.clearLocalAssociation()
        scheduler.cancelAll(metadata.all().map { it.reminderId })
    }

    /**
     * Delivery is non-fatal per reminder: any failure except Unauthorized becomes an
     * empty list (the UI keeps the reminder). Unauthorized must propagate so the
     * pairing expires and the user re-pairs.
     */
    private fun deliveryFor(reminderId: String): List<DeviceApiClient.DeliveryView> = try {
        api.delivery(reminderId)
    } catch (e: DeviceApiClient.ApiException.Unauthorized) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private fun fcmBackend(): FcmBackend = object : FcmBackend {
        override suspend fun registerFcmToken(registrationToken: String) {
            api.registerFcmToken(registrationToken)
        }
    }
}

/** Result of a full refresh: trips, reminders, per-reminder delivery, degraded-alarm flag. */
data class SyncResult(
    val trips: List<DeviceApiClient.TripView>,
    val reminders: List<DeviceApiClient.ReminderView>,
    val delivery: Map<String, List<DeviceApiClient.DeliveryView>>,
    val degradedAlarm: Boolean,
)
