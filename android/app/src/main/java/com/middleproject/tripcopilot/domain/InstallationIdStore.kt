package com.middleproject.tripcopilot.domain

/**
 * Stable installation identity for pairing. The ID is created once and reused for
 * every pairing exchange so a device does not accumulate backend device records.
 * Storage is injected so JVM tests are deterministic.
 */
interface InstallationIdStore {

    /** Returns the existing stable ID, creating and persisting it on first use. */
    fun getOrCreate(): String
}
