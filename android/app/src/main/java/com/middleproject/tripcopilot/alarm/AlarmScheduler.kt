package com.middleproject.tripcopilot.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.middleproject.tripcopilot.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules exact alarms when permitted, otherwise a safe inexact fallback. Every
 * reminder maps to one stable PendingIntent, so repeated scheduling replaces the
 * previous alarm instead of stacking duplicates. When an alarm fires it posts a
 * channel notification and clears/marks the local metadata as fired.
 */
class AlarmScheduler(context: Context, private val metadata: AlarmMetadataStore) : AlarmSchedulerBoundary {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(alarm: LocalAlarm) {
        metadata.upsert(alarm)
        val pending = pendingIntent(alarm.reminderId)
        val triggerAt = alarm.triggerAtEpochMillis
        val canExact = canScheduleExact()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    override fun cancel(reminderId: String) {
        metadata.remove(reminderId)
        alarmManager.cancel(pendingIntent(reminderId))
    }

    /** Removes every scheduled alarm for the supplied reminder ids. */
    override fun cancelAll(reminderIds: Collection<String>) {
        reminderIds.forEach { cancel(it) }
    }

    override fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    /** POST_NOTIFICATIONS (API 33+) gates notification posting; alarm metadata is still cleared. */
    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM_FIRED)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .setData(android.net.Uri.parse("tripcopilot://alarm/$reminderId"))
        return PendingIntent.getBroadcast(
            appContext,
            AlarmIdentity.requestCode(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun onAlarmFired(reminderId: String) {
        val alarm = metadata.findByReminderId(reminderId) ?: return
        val firedAt = Instant.ofEpochMilli(alarm.triggerAtEpochMillis)
        postNotification(alarm.reminderId, alarm.title, alarm.message)
        metadata.remove(reminderId)
    }

    private fun postNotification(reminderId: String, title: String, message: String) {
        if (!canPostNotifications()) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify(AlarmIdentity.requestCode(reminderId), notification)
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.middleproject.tripcopilot.action.ALARM_FIRED"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_TAG = "trip-copilot-alarm"
    }
}
