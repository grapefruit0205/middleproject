package com.middleproject.tripcopilot.data

import android.content.Context
import com.middleproject.tripcopilot.alarm.AndroidAlarmMetadataStore
import com.middleproject.tripcopilot.alarm.AlarmMetadataStore

/** Wires the platform store implementations; kept separate for testability. */
object AndroidAlarmMetadataStoreProvider {

    @Volatile
    private var instance: AlarmMetadataStore? = null

    fun get(context: Context): AlarmMetadataStore {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            return AndroidAlarmMetadataStore(context).also { instance = it }
        }
    }
}
