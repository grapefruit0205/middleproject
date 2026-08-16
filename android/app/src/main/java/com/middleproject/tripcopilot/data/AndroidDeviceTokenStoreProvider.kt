package com.middleproject.tripcopilot.data

import android.content.Context
import com.middleproject.tripcopilot.domain.DeviceTokenStore

/** Wires the platform token store implementation; kept separate for testability. */
object AndroidDeviceTokenStoreProvider {

    @Volatile
    private var instance: DeviceTokenStore? = null

    fun get(context: Context): DeviceTokenStore {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            return AndroidDeviceTokenStore(context).also { instance = it }
        }
    }
}
