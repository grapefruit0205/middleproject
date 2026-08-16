package com.middleproject.tripcopilot.domain

import com.middleproject.tripcopilot.alarm.AlarmMetadataStore
import com.middleproject.tripcopilot.alarm.LocalAlarm

/** Durable local alarm metadata keyed by reminder UUID. */
interface AlarmMetadataStore {

    /** Replaces any existing alarm for this reminder with the new one. */
    fun upsert(alarm: LocalAlarm)

    fun findByReminderId(reminderId: String): LocalAlarm?

    fun remove(reminderId: String)

    fun all(): List<LocalAlarm>
}
