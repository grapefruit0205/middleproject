package com.middleproject.tripcopilot.domain

import java.util.UUID

/**
 * A paired device credential: the opaque bearer token plus its expiry.
 * Never logged, never placed in resources/build config.
 */
data class DeviceCredential(val token: String, val expiresAtEpochMillis: Long) {

    fun isValidAt(nowEpochMillis: Long): Boolean =
        token.isNotBlank() && nowEpochMillis < expiresAtEpochMillis
}

/** Persistence boundary for the device credential so JVM tests are deterministic. */
interface DeviceTokenStore {
    fun load(): DeviceCredential?
    fun save(credential: DeviceCredential)
    fun clear()

    /** True when a valid unexpired local credential exists. */
    fun hasValidCredential(): Boolean =
        load()?.isValidAt(System.currentTimeMillis()) == true
}

/** Stable per-operation idempotency key generation. */
object IdempotencyKeys {
    fun newKey(): String = UUID.randomUUID().toString()
}
