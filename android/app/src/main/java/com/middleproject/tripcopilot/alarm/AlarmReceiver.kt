package com.middleproject.tripcopilot.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a scheduled alarm triggers: posts the notification and clears metadata. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM_FIRED) return
        val reminderId = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val scheduler = AppAlarmComponents.scheduler(context)
        scheduler.onAlarmFired(reminderId)
    }
}
