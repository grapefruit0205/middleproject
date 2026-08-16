package com.middleproject.tripcopilot.data

import android.content.Context
import com.middleproject.tripcopilot.domain.FcmLocalStateStore

/**
 * SharedPreferences-backed local FCM association/state. Only a boolean presence flag is
 * stored — never the raw FCM token and never any bearer credential.
 */
class AndroidFcmLocalStateStore(context: Context) : FcmLocalStateStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isAssociated(): Boolean = prefs.getBoolean(KEY_FCM_ASSOCIATED, false)

    /** Marks that an FCM token has been registered locally; stores no raw token. */
    override fun markAssociated() {
        prefs.edit().putBoolean(KEY_FCM_ASSOCIATED, true).apply()
    }

    override fun clearLocalAssociation() {
        prefs.edit().remove(KEY_FCM_ASSOCIATED).apply()
    }

    private companion object {
        const val PREFS_NAME = "trip_copilot_fcm"
        const val KEY_FCM_ASSOCIATED = "fcm_associated"
    }
}
