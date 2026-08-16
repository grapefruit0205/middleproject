package com.middleproject.tripcopilot.domain

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.LocalAlarm

/** Cancels scheduled local alarms. Implemented by the platform scheduler. */
interface AlarmCancelBoundary {
    fun cancel(reminderId: String)

    fun cancelAll(reminderIds: Collection<String>)
}

/**
 * Bounded backend disconnect boundary: server-side device disconnect and best-effort
 * FCM token deregistration. Implemented by the HTTP API client so JVM tests are
 * deterministic. Neither method re-authenticates and neither takes a token argument;
 * headers and tokens are never logged by callers.
 */
interface BackendDisconnectClient {
    suspend fun disconnectDevice()

    suspend fun deleteFcmRegistration()
}

/** Minimal local FCM-association/state boundary so JVM tests are deterministic. */
interface FcmLocalStateStore {
    fun isAssociated(): Boolean

    fun markAssociated()

    fun clearLocalAssociation()
}

/**
 * Coordinates device disconnect: attempts the server disconnect, then best-effort FCM
 * deregistration while the credential still exists, and regardless of any server
 * exception always clears the local credential, local FCM state, and every stored local
 * alarm. Returns whether the server disconnect succeeded; it never re-authenticates
 * locally and never logs tokens or headers.
 */
class DeviceDisconnectCoordinator(
    private val backend: BackendDisconnectClient,
    private val tokenStore: DeviceTokenStore,
    private val fcmLocalState: FcmLocalStateStore,
    private val metadata: AlarmMetadataStore,
    private val alarms: AlarmCancelBoundary,
) {

    suspend fun disconnect(): Boolean {
        var serverOk = true
        try {
            backend.disconnectDevice()
        } catch (_: Exception) {
            serverOk = false
        }
        try {
            backend.deleteFcmRegistration()
        } catch (_: Exception) {
            // Local cleanup continues regardless.
        }
        tokenStore.clear()
        fcmLocalState.clearLocalAssociation()
        alarms.cancelAll(metadata.all().map { it.reminderId })
        return serverOk
    }
}
