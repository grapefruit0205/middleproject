package com.middleproject.tripcopilot.alarm

import android.content.Context
import com.middleproject.tripcopilot.data.AndroidAlarmMetadataStoreProvider
import com.middleproject.tripcopilot.data.AndroidDeviceTokenStore

/** Small service-locator so receivers can build the scheduler without a DI framework. */
object AppAlarmComponents {

    @Volatile
    private var scheduler: AlarmScheduler? = null

    fun scheduler(context: Context): AlarmScheduler {
        scheduler?.let { return it }
        synchronized(this) {
            scheduler?.let { return it }
            val app = context.applicationContext
            val metadata = AndroidAlarmMetadataStoreProvider.get(app)
            return AlarmScheduler(app, metadata).also { scheduler = it }
        }
    }
}
