package com.middleproject.tripcopilot.data

import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore

/**
 * Holds the paired device credential (opaque bearer token + expiry) in memory.
 * Production persistence is provided by [AndroidDeviceTokenStore] which stores
 * ciphertext/IV in private preferences and the AES-GCM key in Android Keystore.
 */
class InMemoryDeviceTokenStore : DeviceTokenStore {

    private var credential: DeviceCredential? = null

    @Synchronized
    override fun load(): DeviceCredential? = credential

    @Synchronized
    override fun save(credential: DeviceCredential) {
        this.credential = credential
    }

    @Synchronized
    override fun clear() {
        credential = null
    }
}
