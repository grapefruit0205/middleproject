package com.middleproject.tripcopilot.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmScheduler
import com.middleproject.tripcopilot.alarm.AndroidAlarmMetadataStore
import com.middleproject.tripcopilot.data.AndroidDeviceTokenStore
import com.middleproject.tripcopilot.data.AndroidFcmLocalStateStore
import com.middleproject.tripcopilot.data.DeviceApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FCM delivery entry point. Defers the reminder decision to the shared
 * [handleFcmReminderMessage] handler: active future updates schedule exactly one
 * alarm, terminal updates (CANCELLED/ACKNOWLEDGED/DELIVERY_FAILED/SCHEDULE_FAILED)
 * cancel that reminder's existing alarm immediately, and anything malformed, unknown,
 * or past is rejected without side effects. Deduplication runs through
 * [ReminderAlarmCoordinator], and the backend registration is refreshed on token
 * refresh without ever logging the token or payload. Phase 16 compiles without
 * google-services.json or a Firebase server credential; runtime Firebase
 * project/network wiring is Phase 17 and fails gracefully.
 */
class TripCopilotMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val now = System.currentTimeMillis()
        val metadata = AndroidAlarmMetadataStore(this)
        val coordinator = ReminderAlarmCoordinator(AlarmScheduler(this, metadata))
        handleFcmReminderMessage(message.data, now, coordinator)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // Backend registration runs off the Firebase callback thread; never log the token.
        scope.launch {
            try {
                val tokenStore = AndroidDeviceTokenStore(this@TripCopilotMessagingService)
                val client = DeviceApiClient(BackendConfig.baseUrl(this@TripCopilotMessagingService), tokenStore)
                if (client.hasValidCredential()) {
                    client.registerFcmToken(token)
                    // Mark associated only after backend registration succeeds; on
                    // failure the flag stays false so the next refresh retries.
                    AndroidFcmLocalStateStore(this@TripCopilotMessagingService).markAssociated()
                }
            } catch (_: Exception) {
                // Phase 17 wires the real Firebase project; failures here are non-fatal
                // and the local association is intentionally left unmarked.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private object BackendConfig {
        fun baseUrl(context: android.content.Context): String =
            com.middleproject.tripcopilot.data.BackendConfig.baseUrl(context)
    }
}
