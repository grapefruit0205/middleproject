package com.middleproject.tripcopilot

import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.data.InMemoryDeviceTokenStore
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Bounded HTTP contract for the API client: unauthenticated exchange, exact Bearer
 * auth only when the credential is valid, no network calls for expired credentials,
 * and nonblank Idempotency-Key on every write. Tokens, headers, and bodies are never
 * printed by this test.
 */
class DeviceApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: DeviceTokenStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = InMemoryDeviceTokenStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): DeviceApiClient = DeviceApiClient(
        baseUrl = server.url("/").toString(),
        tokenStore = tokenStore,
        clockMillis = { 1_000L },
    )

    private fun activeCredential(): DeviceCredential =
        DeviceCredential("credential-token", expiresAtEpochMillis = 2_000L)

    @Test
    fun `exchange sends no Authorization header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"token":"t1","deviceId":"d1","expiresAt":"2026-01-01T00:00:00Z"}"""
                )
        )

        val result = client().exchange(pairingCode = "ABC12-DEF34", installationId = "i1", label = "test")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/device/exchange", request.path)
        assertFalse(request.headers.toMultimap().containsKey("Authorization"))
        assertEquals(result.token, "t1")
        assertEquals(result.deviceId, "d1")
        assertEquals(result.expiresAtEpochMillis, 1_767_225_600_000L)
    }

    @Test
    fun `authenticated read sends exact Bearer only when credential valid`() {
        tokenStore.save(activeCredential())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        client().trips()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/device/trips", request.path)
        val authorization = request.headers["Authorization"]
        assertEquals("Bearer credential-token", authorization)
        assertFalse(authorization.orEmpty().contains("credential-token,"))
        assertFalse(authorization.orEmpty().contains(","))
    }

    @Test
    fun `trip read treats Android JSON null timestamp representation as absent`() {
        tokenStore.save(activeCredential())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":"trip-1","departure":"Suwon","destination":"Busan","departureAt":"null","returnAt":null,"status":"DRAFT","version":1}]"""
                )
        )

        val trip = client().trips().single()

        assertEquals(null, trip.departureAtEpochMillis)
        assertEquals(null, trip.returnAtEpochMillis)
    }

    @Test
    fun `expired credential makes no network request`() {
        tokenStore.save(DeviceCredential("expired-token", expiresAtEpochMillis = 1_000L))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        try {
            client().trips()
            fail("Expected Unauthorized for expired credential")
        } catch (e: DeviceApiClient.ApiException.Unauthorized) {
            // expected
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing credential makes no network request`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        try {
            client().trips()
            fail("Expected Unauthorized for missing credential")
        } catch (e: DeviceApiClient.ApiException.Unauthorized) {
            // expected
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `transit favorites are owner scoped server records for widget lookup`() {
        tokenStore.save(activeCredential())
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """[{"id":"f1","alias":"회사 앞","mode":"BUS","stationName":null,"cityCode":11,"nodeId":"STOP-1","stopName":"강남역11번출구","routeNo":"341","updatedAt":"2026-08-17T10:00:00+09:00"}]""",
        ))

        val favorite = client().transitFavorites().single()

        assertEquals("회사 앞", favorite.alias)
        assertEquals("STOP-1", favorite.nodeId)
        assertEquals("/api/device/transport/favorites", server.takeRequest().path)
    }

    @Test
    fun `write operations carry nonblank idempotency keys`() {
        tokenStore.save(activeCredential())
        val server = server

        fun enqueueOk() {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            )
        }

        enqueueOk()
        client().cancelTrip("trip-1", expectedVersion = 3L)
        enqueueOk()
        client().cancelReminder("rem-1", expectedVersion = 4L)
        enqueueOk()
        client().ackReminder("rem-2", expectedVersion = 5L)
        enqueueOk()
        client().registerFcmToken("fcm-token-1")
        enqueueOk()
        client().deleteFcmToken()

        val paths = mutableListOf<String>()
        repeat(5) {
            val request = server.takeRequest()
            paths += request.path.orEmpty()
            val key = request.headers["Idempotency-Key"]
            assertTrue("Missing idempotency key on ${request.path}", key != null && key.isNotBlank())
        }

        assertEquals(
            listOf(
                "/api/device/trips/trip-1/cancel",
                "/api/device/reminders/rem-1/cancel",
                "/api/device/reminders/rem-2/ack",
                "/api/device/fcm-token",
                "/api/device/fcm-token",
            ),
            paths,
        )
    }

    @Test
    fun `disconnect is an authenticated write without an idempotency key`() {
        tokenStore.save(activeCredential())
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client().disconnect()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/device/disconnect", request.path)
        assertEquals(null, request.headers["Idempotency-Key"])
    }

    @Test
    fun `transport read sends bearer encodes query and parses typed envelope`() {
        tokenStore.save(activeCredential())
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"success":true,"empty":false,"retryable":false,"failureKind":null,"value":[{"stationName":"강남","subwayLine":"2호선"}],"errorMessage":null}"""
            )
        )

        val result = client().realtimeSubwayArrivals("강남 역", 5)

        val request = server.takeRequest()
        assertEquals("Bearer credential-token", request.headers["Authorization"])
        assertEquals("강남 역", request.requestUrl?.queryParameter("stationName"))
        assertEquals("5", request.requestUrl?.queryParameter("limit"))
        assertTrue(result.success)
        assertEquals("강남", result.items.single().fields["stationName"])
        assertEquals("2호선", result.items.single().fields["subwayLine"])
    }

    @Test
    fun `transport failure remains typed and never becomes a fake empty success`() {
        tokenStore.save(activeCredential())
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"success":false,"empty":false,"retryable":true,"failureKind":"TIMEOUT","value":null,"errorMessage":"Provider request timed out"}"""
            )
        )

        val result = client().nearbyBusStops(37.5665, 126.9780)

        assertFalse(result.success)
        assertFalse(result.empty)
        assertTrue(result.retryable)
        assertEquals("TIMEOUT", result.failureKind)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `official handoffs parse as https links`() {
        tokenStore.save(activeCredential())
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"korail":"https://www.letskorail.com/","srt":"https://etk.srail.kr/","tmoneyIntercityBus":"https://txbus.t-money.co.kr/"}"""
            )
        )

        val links = client().transportHandoffs()

        assertEquals("https://www.letskorail.com/", links["korail"])
        assertTrue(links.values.all { it.startsWith("https://") })
    }
}
