package com.middleproject.tripcopilot.fcm

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase Android adapter for [FcmTokenProvider]. Awaits
 * [FirebaseMessaging.getInstance().token] via Task listeners. When the Firebase
 * runtime configuration is missing, or the token Task fails, [currentToken] returns
 * null instead of throwing: FCM unavailability is non-fatal for the device and a later
 * refresh retries. No google-services.json is needed at compile time and no
 * coroutine-play-services dependency is required.
 */
class FirebaseFcmTokenProvider : FcmTokenProvider {

    @Suppress("DEPRECATION")
    override suspend fun currentToken(): String? {
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (_: Exception) {
            return null
        }
        return try {
            suspendCancellableCoroutine { continuation ->
                @Suppress("DEPRECATION")
                val task = messaging.token
                task.addOnSuccessListener { token ->
                    if (continuation.isActive) continuation.resume(token)
                }
                task.addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                continuation.invokeOnCancellation { /* Task keeps running; result is dropped. */ }
            }
        } catch (_: Exception) {
            null
        }
    }
}
