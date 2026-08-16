package com.middleproject.tripcopilot.data

import android.content.Context
import com.middleproject.tripcopilot.domain.InstallationIdStore
import java.util.UUID

/**
 * SharedPreferences-backed stable installation identity. Creates `android-<UUID>` once
 * and reuses it for every pairing exchange. The ID is not secret; it never contains
 * credential or FCM token material.
 */
class AndroidInstallationIdStore(context: Context) : InstallationIdStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getOrCreate(): String {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        synchronized(this) {
            prefs.getString(KEY_INSTALLATION_ID, null)?.let { return it }
            val created = "android-${UUID.randomUUID()}"
            prefs.edit().putString(KEY_INSTALLATION_ID, created).apply()
            return created
        }
    }

    private companion object {
        const val PREFS_NAME = "trip_copilot_installation"
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
