package com.middleproject.tripcopilot.fcm

import com.middleproject.tripcopilot.domain.FcmLocalStateStore
import com.middleproject.tripcopilot.domain.DeviceTokenStore

/**
 * Best-effort FCM registration lifecycle. Registers the current token when a valid
 * credential exists, and marks the local association only after backend registration
 * succeeds. When the backend registration fails the local association stays false so
 * a later refresh retries. The raw token is never logged or persisted. Never throws:
 * FCM unavailability (missing runtime config, no network) is non-fatal.
 */
class FcmRegistrationCoordinator(
    private val tokenProvider: FcmTokenProvider,
    private val tokenStore: DeviceTokenStore,
    private val fcmLocalState: FcmLocalStateStore,
    private val backend: FcmBackend,
) {

    suspend fun registerIfNeeded() {
        if (fcmLocalState.isAssociated()) return
        if (!tokenStore.hasValidCredential()) return
        val token = try {
            tokenProvider.currentToken()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            backend.registerFcmToken(token)
        } catch (_: Exception) {
            return
        }
        fcmLocalState.markAssociated()
    }
}
