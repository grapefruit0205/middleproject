package com.middleproject.tripcopilot.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityPolicyTest {
    private val policy = LocationQualityPolicy(maxAgeMillis = 60_000, maxAccuracyMeters = 100f)

    @Test
    fun `accepts a fresh high accuracy foreground fix`() {
        assertTrue(policy.evaluate(nowMillis = 100_000, fixTimeMillis = 70_000, accuracyMeters = 24f) is LocationQuality.Accepted)
    }

    @Test
    fun `rejects a stale cached fix`() {
        assertEquals(LocationQuality.Stale, policy.evaluate(100_000, 30_000, 20f))
    }

    @Test
    fun `rejects an imprecise fix before querying nearby stops`() {
        assertEquals(LocationQuality.Imprecise, policy.evaluate(100_000, 90_000, 180f))
    }
}
