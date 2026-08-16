package com.middleproject.tripcopilot.fcm

import com.middleproject.tripcopilot.data.InMemoryDeviceTokenStore
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import com.middleproject.tripcopilot.domain.FcmLocalStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract of [FcmRegistrationCoordinator]: registration happens once after a
 * successful backend call, a failure leaves the local association unmarked so a later
 * retry succeeds, and no call is made when already associated or without a credential.
 */
class FcmRegistrationCoordinatorTest {

    private lateinit var tokenStore: DeviceTokenStore
    private lateinit var fcmLocalState: InMemoryFcmLocalStateStore
    private lateinit var backend: RecordingFcmBackend

    @Before
    fun setUp() {
        tokenStore = InMemoryDeviceTokenStore()
        tokenStore.save(DeviceCredential("credential-token", expiresAtEpochMillis = 2_000_000_000_000L))
        fcmLocalState = InMemoryFcmLocalStateStore()
        backend = RecordingFcmBackend()
    }

    private fun coordinator(token: String?) = FcmRegistrationCoordinator(
        tokenProvider = FixedTokenProvider(token),
        tokenStore = tokenStore,
        fcmLocalState = fcmLocalState,
        backend = backend,
    )

    @Test
    fun `successful registration marks associated and registers only once`() {
        runBlocking {
            coordinator("fcm-token").registerIfNeeded()
            coordinator("fcm-token").registerIfNeeded()
        }

        assertTrue(fcmLocalState.associated)
        assertEquals(1, backend.tokens.size)
        assertEquals("fcm-token", backend.tokens.single())
    }

    @Test
    fun `failed registration does not mark and later retry succeeds`() {
        backend.failCount = 1
        runBlocking {
            coordinator("fcm-token").registerIfNeeded()
        }
        assertFalse(fcmLocalState.associated)
        assertEquals(0, backend.tokens.size)

        runBlocking {
            coordinator("fcm-token").registerIfNeeded()
        }
        assertTrue(fcmLocalState.associated)
        assertEquals(1, backend.tokens.size)
    }

    @Test
    fun `no registration when already associated`() {
        fcmLocalState.associated = true

        runBlocking {
            coordinator("fcm-token").registerIfNeeded()
        }

        assertEquals(0, backend.tokens.size)
    }

    @Test
    fun `no registration when token provider returns null`() {
        runBlocking {
            coordinator(null).registerIfNeeded()
        }

        assertFalse(fcmLocalState.associated)
        assertEquals(0, backend.tokens.size)
    }

    @Test
    fun `no registration without a valid credential`() {
        tokenStore.clear()

        runBlocking {
            coordinator("fcm-token").registerIfNeeded()
        }

        assertFalse(fcmLocalState.associated)
        assertEquals(0, backend.tokens.size)
    }

    @Test
    fun `throwing token provider is non-fatal and never marks associated or calls backend`() {
        runBlocking {
            FcmRegistrationCoordinator(
                tokenProvider = object : FcmTokenProvider {
                    override suspend fun currentToken(): String? =
                        throw IllegalStateException("Firebase runtime config missing")
                },
                tokenStore = tokenStore,
                fcmLocalState = fcmLocalState,
                backend = backend,
            ).registerIfNeeded()
        }

        assertFalse(fcmLocalState.associated)
        assertEquals(0, backend.tokens.size)
    }
}

private class FixedTokenProvider(private val token: String?) : FcmTokenProvider {
    override suspend fun currentToken(): String? = token
}

private class RecordingFcmBackend : FcmBackend {
    val tokens = mutableListOf<String>()
    var failCount = 0

    override suspend fun registerFcmToken(registrationToken: String) {
        if (failCount > 0) {
            failCount--
            throw IllegalStateException("backend unreachable")
        }
        tokens += registrationToken
    }
}

private class InMemoryFcmLocalStateStore : FcmLocalStateStore {
    var associated = false

    override fun isAssociated(): Boolean = associated

    override fun markAssociated() {
        associated = true
    }

    override fun clearLocalAssociation() {
        associated = false
    }
}
