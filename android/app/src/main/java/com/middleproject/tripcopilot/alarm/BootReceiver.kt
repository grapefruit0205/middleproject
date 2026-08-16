package com.middleproject.tripcopilot.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.middleproject.tripcopilot.data.AndroidAlarmMetadataStoreProvider
import com.middleproject.tripcopilot.data.AndroidDeviceTokenStoreProvider
import com.middleproject.tripcopilot.domain.DeviceTokenStore

/**
 * Reschedules only future, active reminders after BOOT_COMPLETED, TIME_SET, and
 * TIMEZONE_CHANGED. With a valid local device credential, old registrations are
 * cancelled, past alarms dropped, and only future alarms rescheduled; without one,
 * every locally known scheduled alarm is cancelled and its metadata removed so
 * previously registered AlarmManager alarms cannot survive. Storage is injected so
 * JVM tests are deterministic.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store: DeviceTokenStore = AndroidDeviceTokenStoreProvider.get(app)
        val alarms = AndroidAlarmMetadataStoreProvider.get(app)
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val scheduler = AppAlarmComponents.scheduler(context)
        reschedule(store, alarms, System.currentTimeMillis(), scheduler)
    }

    internal fun reschedule(
        store: DeviceTokenStore,
        alarms: AlarmMetadataStore,
        nowEpochMillis: Long,
        scheduler: AlarmSchedulerBoundary,
    ) {
        val credential = store.load()
        if (credential == null || !credential.isValidAt(nowEpochMillis)) {
            // No valid local credential: cancel every locally known alarm so previously
            // registered AlarmManager alarms cannot survive a boot or time change.
            ReschedulePolicy.cancelAll(alarms, scheduler)
            return
        }
        val keep = ReschedulePolicy.futureAlarms(alarms, nowEpochMillis)
        scheduler.cancelAll(alarms.all().map { it.reminderId })
        keep.forEach { alarm ->
            try {
                scheduler.schedule(alarm)
            } catch (_: Exception) {
                // One corrupt metadata entry or malformed UUID must not crash the
                // whole reschedule pass; skip it and continue.
            }
        }
    }
}

/**
 * Pure reschedule decisions for BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED so JVM tests
 * are deterministic: without a valid credential every locally known scheduled alarm is
 * cancelled and its metadata removed; with one, old registrations are cancelled and
 * only future alarms are rescheduled.
 */
object ReschedulePolicy {

    fun cancelAll(alarms: AlarmMetadataStore, scheduler: AlarmSchedulerBoundary) {
        val all = alarms.all()
        scheduler.cancelAll(all.map { it.reminderId })
    }

    fun futureAlarms(alarms: AlarmMetadataStore, nowEpochMillis: Long): List<LocalAlarm> =
        alarms.all().filter { it.triggerAtEpochMillis > nowEpochMillis }
}
