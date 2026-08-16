package com.middleproject.tripcopilot.fcm

/** Narrow backend boundary for FCM registration so JVM tests are deterministic. */
interface FcmBackend {
    suspend fun registerFcmToken(registrationToken: String)
}
