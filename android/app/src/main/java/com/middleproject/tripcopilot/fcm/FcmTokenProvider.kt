package com.middleproject.tripcopilot.fcm

/**
 * Bounded FCM registration-token boundary. Implementations return the current FCM
 * registration token, or null when Firebase runtime configuration is absent (Phase 17
 * wires the real Firebase project). Raw tokens are never logged or persisted by
 * callers and never stored in SharedPreferences.
 */
interface FcmTokenProvider {
    suspend fun currentToken(): String?
}
