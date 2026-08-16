package com.middleproject.tripcopilot

import android.app.Application
import com.middleproject.tripcopilot.alarm.AndroidAlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmScheduler
import com.middleproject.tripcopilot.data.AndroidDeviceTokenStore
import com.middleproject.tripcopilot.data.AndroidFcmLocalStateStore
import com.middleproject.tripcopilot.data.AndroidInstallationIdStore
import com.middleproject.tripcopilot.data.BackendConfig
import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.data.DeviceRepository
import com.middleproject.tripcopilot.domain.AlarmCancelBoundary
import com.middleproject.tripcopilot.domain.DeviceDisconnectCoordinator
import com.middleproject.tripcopilot.fcm.FirebaseFcmTokenProvider

class TripCopilotApplication : Application() {

    lateinit var repository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val tokenStore = AndroidDeviceTokenStore(this)
        val metadata = AndroidAlarmMetadataStore(this)
        val scheduler = AlarmScheduler(this, metadata)
        val fcmLocalState = AndroidFcmLocalStateStore(this)
        val api = DeviceApiClient(BackendConfig.baseUrl(this), tokenStore)
        repository = DeviceRepository(
            api = api,
            tokenStore = tokenStore,
            installationIdStore = AndroidInstallationIdStore(this),
            metadata = metadata,
            scheduler = scheduler,
            disconnectCoordinator = DeviceDisconnectCoordinator(
                backend = api,
                tokenStore = tokenStore,
                fcmLocalState = fcmLocalState,
                metadata = metadata,
                alarms = object : AlarmCancelBoundary {
                    override fun cancel(reminderId: String) = scheduler.cancel(reminderId)
                    override fun cancelAll(reminderIds: Collection<String>) = scheduler.cancelAll(reminderIds)
                },
            ),
            fcmTokenProvider = FirebaseFcmTokenProvider(),
            fcmLocalState = fcmLocalState,
        )
    }
}
