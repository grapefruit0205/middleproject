package com.middleproject.tripcopilot.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences-backed durable alarm metadata keyed by reminder UUID. */
class AndroidAlarmMetadataStore(context: Context) : AlarmMetadataStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun upsert(alarm: LocalAlarm) {
        prefs.edit().putString(key(alarm.reminderId), encode(alarm)).apply()
    }

    override fun findByReminderId(reminderId: String): LocalAlarm? {
        val raw = prefs.getString(key(reminderId), null) ?: return null
        return try {
            decode(raw)
        } catch (e: Exception) {
            null
        }
    }

    override fun remove(reminderId: String) {
        prefs.edit().remove(key(reminderId)).apply()
    }

    override fun all(): List<LocalAlarm> {
        val result = mutableListOf<LocalAlarm>()
        val prefix = "$PREFIX_"
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                try {
                    result.add(decode(v))
                } catch (_: Exception) {
                    // Skip corrupt entries rather than failing the whole reschedule pass.
                }
            }
        }
        return result
    }

    private fun key(reminderId: String) = "$PREFIX_$reminderId"

    private fun encode(alarm: LocalAlarm): String = JSONObject()
        .put("reminderId", alarm.reminderId)
        .put("triggerAtEpochMillis", alarm.triggerAtEpochMillis)
        .put("title", alarm.title)
        .put("message", alarm.message)
        .toString()

    private fun decode(raw: String): LocalAlarm {
        val o = JSONObject(raw)
        return LocalAlarm(
            reminderId = o.getString("reminderId"),
            triggerAtEpochMillis = o.getLong("triggerAtEpochMillis"),
            title = o.optString("title"),
            message = o.optString("message"),
        )
    }

    private companion object {
        const val PREFS_NAME = "trip_copilot_alarms"
        const val PREFIX_ = "alarm"
    }
}
