package com.middleproject.tripcopilot.data

import com.middleproject.tripcopilot.domain.InstallationIdStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory installation identity for JVM tests; no Android framework. */
class InMemoryInstallationIdStore : InstallationIdStore {

    private var id: String? = null

    @Synchronized
    override fun getOrCreate(): String = id ?: "android-${java.util.UUID.randomUUID()}".also { id = it }
}

/**
 * Contract of the stable installation identity: the same ID is returned on every
 * call (create-once/reuse) and is formatted `android-<UUID>`.
 */
class InstallationIdStoreTest {

    private fun store(): InstallationIdStore = InMemoryInstallationIdStore()

    @Test
    fun `creates once and reuses the same id`() {
        val store = store()
        val first = store.getOrCreate()
        assertEquals(first, store.getOrCreate())
        assertEquals(first, store.getOrCreate())
        assertTrue(first.startsWith("android-"))
        assertEquals(8 + 36, first.length)
    }

    @Test
    fun `separate stores create distinct ids`() {
        val first = store().getOrCreate()
        val second = store().getOrCreate()
        assertNotEquals(first, second)
    }
}
