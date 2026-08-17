package com.middleproject.tripcopilot.location

sealed interface LocationQuality {
    data object Accepted : LocationQuality
    data object Stale : LocationQuality
    data object Imprecise : LocationQuality
}

class LocationQualityPolicy(
    private val maxAgeMillis: Long = 60_000,
    private val maxAccuracyMeters: Float = 100f,
) {
    fun evaluate(nowMillis: Long, fixTimeMillis: Long, accuracyMeters: Float): LocationQuality {
        if (fixTimeMillis <= 0 || nowMillis - fixTimeMillis > maxAgeMillis) return LocationQuality.Stale
        if (!accuracyMeters.isFinite() || accuracyMeters > maxAccuracyMeters) return LocationQuality.Imprecise
        return LocationQuality.Accepted
    }
}
