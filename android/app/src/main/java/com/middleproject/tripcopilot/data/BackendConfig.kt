package com.middleproject.tripcopilot.data

import android.content.Context
import com.middleproject.tripcopilot.BuildConfig

/**
 * Configurable backend base URL for emulator/local verification. No live cloud
 * secret is embedded. The emulator reaches the host via 10.0.2.2; cleartext HTTP is
 * enabled only for debug builds via the debug network security config.
 */
object BackendConfig {

    private const val OVERRIDE_PREFS = "trip_copilot_backend"
    private const val KEY_BASE_URL = "base_url"
    private val defaultBackendUrl = BuildConfig.BACKEND_BASE_URL

    fun baseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(OVERRIDE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, null) ?: defaultBackendUrl
    }

    fun setBaseUrl(context: Context, url: String) {
        context.applicationContext.getSharedPreferences(OVERRIDE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, url)
            .apply()
    }
}
