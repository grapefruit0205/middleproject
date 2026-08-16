package com.middleproject.tripcopilot

import com.middleproject.tripcopilot.alarm.AlarmIdentity
import com.middleproject.tripcopilot.alarm.AlarmPolicy
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.PairingCode
import com.middleproject.tripcopilot.fcm.FcmReminderPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bounded contract for the pure domain: pairing-code normalization, credential
 * expiry, alarm identity/policy, and FCM payload validation. No I/O, no logs,
 * no token material printed.
 */
class DomainContractTest {

    // ---- PairingCode ----

    @Test
    fun `pairing code canonical normalizes trim and case`() {
        assertEquals("ABC12-DEF34", PairingCode.canonical("  abc12-def34  "))
        assertEquals("ABC12-DEF34", PairingCode.canonical("ABC12-DEF34"))
        assertEquals("AB123-CD456", PairingCode.canonical("ab123-cd456"))
    }

    @Test
    fun `pairing code invalid shapes are rejected`() {
        assertNull(PairingCode.canonical(null))
        assertNull(PairingCode.canonical(""))
        assertNull(PairingCode.canonical("abc12-def3")) // too short second group
        assertNull(PairingCode.canonical("abc12-def345")) // too long second group
        assertNull(PairingCode.canonical("abc1-def34")) // too short first group
        assertNull(PairingCode.canonical("abc123-def34")) // too long first group
        assertNull(PairingCode.canonical("abc12-def3!")) // non-alphanumeric
        assertNull(PairingCode.canonical("abc12def34")) // missing separator
        assertNull(PairingCode.canonical("abc12-def34-")) // trailing separator
    }

    // ---- DeviceCredential ----

    @Test
    fun `credential is valid strictly before expiry and invalid at and after it`() {
        val credential = DeviceCredential("opaque-token", expiresAtEpochMillis = 1_000L)
        assertTrue(credential.isValidAt(999L))
        assertFalse(credential.isValidAt(1_000L))
        assertFalse(credential.isValidAt(1_001L))
        assertFalse(DeviceCredential("   ", 1_000L).isValidAt(999L))
        assertFalse(DeviceCredential("", 1_000L).isValidAt(999L))
    }

    // ---- AlarmIdentity / AlarmPolicy ----

    @Test
    fun `alarm identity is stable and nonnegative for a reminder uuid`() {
        val id1 = AlarmIdentity.requestCode("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a")
        val id2 = AlarmIdentity.requestCode("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a")
        assertEquals(id1, id2)
        assertTrue(id1 >= 0)
        assertTrue(id2 >= 0)
    }

    @Test
    fun `alarm policy treats only explicit terminal statuses as never schedulable`() {
        assertTrue(AlarmPolicy.isTerminal("CANCELLED"))
        assertTrue(AlarmPolicy.isTerminal("ACKNOWLEDGED"))
        assertTrue(AlarmPolicy.isTerminal("DELIVERY_FAILED"))
        assertTrue(AlarmPolicy.isTerminal("SCHEDULE_FAILED"))
        assertFalse(AlarmPolicy.isTerminal("FAILED"))
        assertFalse(AlarmPolicy.isTerminal("UNKNOWN_STATUS"))
        assertFalse(AlarmPolicy.isTerminal("SCHEDULE_PENDING"))
        assertFalse(AlarmPolicy.isTerminal("SCHEDULED"))
        assertFalse(AlarmPolicy.isTerminal("DISPATCHED"))
        assertFalse(AlarmPolicy.isTerminal("DELIVERED"))
        assertFalse(AlarmPolicy.isTerminal("RETRYING"))
    }

    @Test
    fun `alarm policy schedules only future times for active statuses`() {
        val now = 5_000L
        assertTrue(AlarmPolicy.shouldSchedule("SCHEDULED", alarmTimeEpochMillis = 5_001L, now))
        assertFalse(AlarmPolicy.shouldSchedule("SCHEDULED", 5_000L, now)) // at-now is past
        assertFalse(AlarmPolicy.shouldSchedule("SCHEDULED", 4_999L, now)) // past
        assertFalse(AlarmPolicy.shouldSchedule("SCHEDULED", null, now)) // no time
        assertFalse(AlarmPolicy.shouldSchedule("CANCELLED", 5_001L, now)) // terminal
        assertFalse(AlarmPolicy.shouldSchedule("ACKNOWLEDGED", 5_001L, now)) // terminal
        assertFalse(AlarmPolicy.shouldSchedule("FAILED", 5_001L, now)) // unknown, future time
    }

    // ---- FcmReminderPayload ----

    private fun activePayload(): Map<String, String> = mapOf(
        "reminderId" to "8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a",
        "status" to "SCHEDULED",
        "alarmTime" to "9_000",
        "title" to "Flight",
        "message" to "Gate changed",
    )

    @Test
    fun `fcm payload rejects bad uuid, oversized strings, and invalid timestamp`() {
        val now = 1_000L
        assertNull(FcmReminderPayload.parse(activePayload() + ("reminderId" to "not-a-uuid"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("reminderId" to "8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("reminderId" to " 8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a "), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("reminderId" to "8D0FF0E0-0F3F-4B1A-9F6D-9E9B1C1A1A1A"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("title" to "x".repeat(301)), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("message" to "y".repeat(301)), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("status" to "z".repeat(301)), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "not-a-number"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "-5"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "0"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "9999999999999"), now))
        assertNull(FcmReminderPayload.parse(null, now))
        assertNull(FcmReminderPayload.parse(emptyMap(), now))
    }

    @Test
    fun `fcm payload rejects terminal or past reminders and accepts bounded active future`() {
        val now = 1_000L
        assertNull(FcmReminderPayload.parse(activePayload() + ("status" to "CANCELLED"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("status" to "ACKNOWLEDGED"), now))
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "999"), now)) // past
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "1000"), now)) // at-now

        val parsed = FcmReminderPayload.parse(activePayload() + ("alarmTime" to "2000"), now)
        assertEquals("8d0ff0e0-0f3f-4b1a-9f6d-9e9b1c1a1a1a", parsed!!.reminderId)
        assertEquals("SCHEDULED", parsed.status)
        assertEquals(2_000L, parsed.alarmTimeEpochMillis)
        assertEquals("Flight", parsed.title)
        assertEquals("Gate changed", parsed.message)
    }

    @Test
    fun `fcm payload rejects alarm times beyond the bounded future window`() {
        val now = 1_000L
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "7776002000"), now)) // just past 90 days
        assertNull(FcmReminderPayload.parse(activePayload() + ("alarmTime" to "9999999999999"), now))
    }
}