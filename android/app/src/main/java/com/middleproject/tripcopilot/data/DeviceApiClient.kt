package com.middleproject.tripcopilot.data

import com.middleproject.tripcopilot.domain.BackendDisconnectClient
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import com.middleproject.tripcopilot.domain.IdempotencyKeys
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Backend boundary for the repository. Implemented by [DeviceApiClient]; kept separate
 * so JVM tests can fake the backend deterministically. Never logs request/response
 * bodies, Authorization headers, pairing codes, device tokens, or FCM tokens.
 */
interface DeviceRefreshBackend {
    fun hasValidCredential(): Boolean

    fun exchange(pairingCode: String, installationId: String, label: String): DeviceApiClient.ExchangeResult

    fun trips(): List<DeviceApiClient.TripView>

    fun reminders(): List<DeviceApiClient.ReminderView>

    fun delivery(reminderId: String): List<DeviceApiClient.DeliveryView>

    fun cancelTrip(tripId: String, expectedVersion: Long)

    fun cancelReminder(reminderId: String, expectedVersion: Long)

    fun ackReminder(reminderId: String, expectedVersion: Long)

    fun registerFcmToken(registrationToken: String)

    fun deleteFcmToken()

    fun realtimeSubwayArrivals(stationName: String, limit: Int = 20): DeviceApiClient.TransportResult =
        DeviceApiClient.TransportResult.unavailable()

    fun nearbyBusStops(latitude: Double, longitude: Double): DeviceApiClient.TransportResult =
        DeviceApiClient.TransportResult.unavailable()

    fun busStopsByLandmark(landmark: String): DeviceApiClient.TransportResult =
        DeviceApiClient.TransportResult.unavailable()

    fun busArrivals(cityCode: Int, nodeId: String): DeviceApiClient.TransportResult =
        DeviceApiClient.TransportResult.unavailable()

    fun expressBusArrivals(depTerminalCode: String, arrTerminalCode: String): DeviceApiClient.TransportResult =
        DeviceApiClient.TransportResult.unavailable()

    fun intercityBusSchedule(
        depTerminalId: String,
        arrTerminalId: String,
        depPlandTime: String,
    ): DeviceApiClient.TransportResult = DeviceApiClient.TransportResult.unavailable()

    fun transportHandoffs(): Map<String, String> = emptyMap()

    fun transitFavorites(): List<DeviceApiClient.TransitFavorite> = emptyList()
}

/**
 * Bounded backend API client for the Android companion. It builds requests, applies
 * Authorization only when a valid unexpired local credential exists, adds stable
 * Idempotency-Key headers for every applicable write, and never logs request/response
 * bodies or Authorization headers. Pairing codes, device tokens, and FCM tokens are
 * never written to logs.
 */
class DeviceApiClient(
    baseUrl: String,
    private val tokenStore: DeviceTokenStore,
    private val client: OkHttpClient = defaultClient(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : BackendDisconnectClient, DeviceRefreshBackend {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    sealed class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause) {
        class Network(message: String, cause: Throwable? = null) : ApiException(message, cause)
        class Unauthorized(message: String = "Credential missing, expired, or rejected by the server") : ApiException(message)
        class Conflict(message: String) : ApiException(message)
        class Server(message: String, val status: Int) : ApiException(message)
        class Invalid(message: String) : ApiException(message)
    }

    data class ExchangeResult(val token: String, val deviceId: String, val expiresAtEpochMillis: Long)

    data class TripView(
        val id: String,
        val departure: String,
        val destination: String,
        val departureAtEpochMillis: Long?,
        val returnAtEpochMillis: Long?,
        val status: String,
        val version: Long,
    )

    data class ReminderView(
        val id: String,
        val eventId: String,
        val policyId: String,
        val tripId: String?,
        val status: String,
        val version: Long,
        val alarmTimeEpochMillis: Long?,
    )

    data class DeliveryView(val channel: String, val status: String, val attemptedAtEpochMillis: Long?)

    data class TransportItem(val fields: Map<String, String>)

    data class TransitFavorite(
        val id: String,
        val alias: String,
        val mode: String,
        val stationName: String?,
        val cityCode: Int?,
        val nodeId: String?,
        val stopName: String?,
        val routeNo: String?,
    )

    data class TransportResult(
        val success: Boolean,
        val empty: Boolean,
        val retryable: Boolean,
        val failureKind: String?,
        val items: List<TransportItem>,
        val errorMessage: String?,
    ) {
        companion object {
            fun unavailable() = TransportResult(
                success = false,
                empty = false,
                retryable = false,
                failureKind = "UNAVAILABLE",
                items = emptyList(),
                errorMessage = "Transport lookup is unavailable",
            )
        }
    }

    /** True when a valid unexpired local credential exists. */
    override fun hasValidCredential(): Boolean {
        val credential = tokenStore.load() ?: return false
        return credential.isValidAt(clockMillis())
    }

    /** Pairing exchange is deliberately unauthenticated and one-time. */
    override fun exchange(pairingCode: String, installationId: String, label: String): ExchangeResult {
        val payload = JSONObject()
            .put("pairingCode", pairingCode)
            .put("installationId", installationId)
            .put("label", label)
        val response = execute(requestBuilder("/api/device/exchange")
            .post(payload.toString().toRequestBody(JSON))
            .build())
        val body = parse(response)
        return try {
            val token = body.getString("token")
            val deviceId = body.getString("deviceId")
            val expiresAt = parseIsoInstant(body.getString("expiresAt"))
            ExchangeResult(token, deviceId, expiresAt)
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid exchange response")
        }
    }

    override fun trips(): List<TripView> {
        val body = get("/api/device/trips")
        return try {
            val array = JSONArray(body)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                TripView(
                    id = o.getString("id"),
                    departure = o.optString("departure"),
                    destination = o.optString("destination"),
                    departureAtEpochMillis = optEpochMillis(o, "departureAt"),
                    returnAtEpochMillis = optEpochMillis(o, "returnAt"),
                    status = o.optString("status"),
                    version = o.optLong("version"),
                )
            }
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid trips response")
        }
    }

    override fun reminders(): List<ReminderView> {
        val body = get("/api/device/reminders")
        return try {
            val array = JSONArray(body)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ReminderView(
                    id = o.getString("id"),
                    eventId = o.optString("eventId"),
                    policyId = o.optString("policyId"),
                    tripId = o.optString("tripId").takeIf { it.isNotBlank() },
                    status = o.optString("status"),
                    version = o.optLong("version"),
                    alarmTimeEpochMillis = optEpochMillis(o, "alarmTime"),
                )
            }
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid reminders response")
        }
    }

    override fun delivery(reminderId: String): List<DeliveryView> {
        val body = get("/api/device/reminders/$reminderId/delivery")
        return try {
            val array = JSONArray(body)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                DeliveryView(
                    channel = o.optString("channel"),
                    status = o.optString("status"),
                    attemptedAtEpochMillis = optEpochMillis(o, "attemptedAt"),
                )
            }
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid delivery response")
        }
    }

    /** Writes: each carries a fresh Idempotency-Key. */
    override fun cancelTrip(tripId: String, expectedVersion: Long) {
        postWrite("/api/device/trips/$tripId/cancel", expectedVersion)
    }

    override fun cancelReminder(reminderId: String, expectedVersion: Long) {
        postWrite("/api/device/reminders/$reminderId/cancel", expectedVersion)
    }

    override fun ackReminder(reminderId: String, expectedVersion: Long) {
        postWrite("/api/device/reminders/$reminderId/ack", expectedVersion)
    }

    override fun registerFcmToken(registrationToken: String) {
        val payload = JSONObject().put("registrationToken", registrationToken)
        val request = authedRequest("/api/device/fcm-token")
            .post(payload.toString().toRequestBody(JSON))
            .header("Idempotency-Key", IdempotencyKeys.newKey())
            .build()
        execute(request)
    }

    override fun deleteFcmToken() {
        val request = authedRequest("/api/device/fcm-token")
            .delete()
            .header("Idempotency-Key", IdempotencyKeys.newKey())
            .build()
        execute(request)
    }

    override fun realtimeSubwayArrivals(stationName: String, limit: Int): TransportResult =
        transportGet(
            "/api/device/transport/subway/arrivals",
            mapOf("stationName" to stationName, "limit" to limit.toString()),
        )

    override fun nearbyBusStops(latitude: Double, longitude: Double): TransportResult =
        transportGet(
            "/api/device/transport/bus/stops/nearby",
            mapOf("latitude" to latitude.toString(), "longitude" to longitude.toString()),
        )

    override fun busStopsByLandmark(landmark: String): TransportResult =
        transportGet(
            "/api/device/transport/bus/stops/by-landmark",
            mapOf("landmark" to landmark),
        )

    override fun busArrivals(cityCode: Int, nodeId: String): TransportResult =
        transportGet(
            "/api/device/transport/bus/arrivals",
            mapOf("cityCode" to cityCode.toString(), "nodeId" to nodeId),
        )

    override fun expressBusArrivals(depTerminalCode: String, arrTerminalCode: String): TransportResult =
        transportGet(
            "/api/device/transport/express-bus/arrivals",
            mapOf("depTerminalCode" to depTerminalCode, "arrTerminalCode" to arrTerminalCode),
        )

    override fun intercityBusSchedule(
        depTerminalId: String,
        arrTerminalId: String,
        depPlandTime: String,
    ): TransportResult = transportGet(
        "/api/device/transport/intercity-bus/schedule",
        mapOf(
            "depTerminalId" to depTerminalId,
            "arrTerminalId" to arrTerminalId,
            "depPlandTime" to depPlandTime,
        ),
    )

    override fun transportHandoffs(): Map<String, String> {
        val body = parse(get("/api/device/transport/handoffs"))
        return buildMap {
            body.keys().forEach { key ->
                val value = body.optString(key)
                if (value.startsWith("https://")) put(key, value)
            }
        }
    }

    override fun transitFavorites(): List<TransitFavorite> {
        val body = get("/api/device/transport/favorites")
        return try {
            val array = JSONArray(body)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                TransitFavorite(
                    id = item.getString("id"),
                    alias = item.getString("alias"),
                    mode = item.getString("mode"),
                    stationName = item.optString("stationName").takeIf { it.isNotBlank() && it != "null" },
                    cityCode = if (item.has("cityCode") && !item.isNull("cityCode")) item.getInt("cityCode") else null,
                    nodeId = item.optString("nodeId").takeIf { it.isNotBlank() && it != "null" },
                    stopName = item.optString("stopName").takeIf { it.isNotBlank() && it != "null" },
                    routeNo = item.optString("routeNo").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid transit favorites response")
        }
    }

    fun disconnect() {
        execute(authedRequest("/api/device/disconnect").post("".toRequestBody(JSON)).build())
    }

    override suspend fun disconnectDevice() {
        disconnect()
    }

    override suspend fun deleteFcmRegistration() {
        deleteFcmToken()
    }

    // ---- internals ----

    private fun get(path: String): String = execute(authedRequest(path).get().build())

    private fun transportGet(path: String, query: Map<String, String>): TransportResult {
        val url = base.newBuilder().encodedPath(path).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val credential = currentCredentialOrThrow()
        val body = execute(
            Request.Builder().url(url).header("Authorization", "Bearer ${credential.token}").get().build()
        )
        return parseTransportResult(body)
    }

    private fun postWrite(path: String, expectedVersion: Long): String {
        val payload = JSONObject().put("expectedVersion", expectedVersion)
        return execute(
            authedRequest(path)
                .post(payload.toString().toRequestBody(JSON))
                .header("Idempotency-Key", IdempotencyKeys.newKey())
                .build()
        )
    }

    private fun authedRequest(path: String): Request.Builder {
        val credential = currentCredentialOrThrow()
        return requestBuilder(path).header("Authorization", "Bearer ${credential.token}")
    }

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder().url(base.newBuilder().encodedPath(path).build())

    /** Exact Authorization Bearer only when a valid unexpired local credential exists. */
    private fun currentCredentialOrThrow(): DeviceCredential {
        val credential = tokenStore.load()
            ?: throw ApiException.Unauthorized("No local device credential")
        if (!credential.isValidAt(clockMillis())) {
            throw ApiException.Unauthorized("Local device credential is expired")
        }
        return credential
    }

    private fun execute(request: Request): String {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw ApiException.Network("Request timed out", e)
        } catch (e: IOException) {
            throw ApiException.Network("Network failure", e)
        }
        response.use { r ->
            val bodyText = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> return bodyText
                r.code == 401 || r.code == 403 -> throw ApiException.Unauthorized()
                r.code == 409 -> throw ApiException.Conflict("The operation conflicts with current server state")
                else -> throw ApiException.Server("Server error ${r.code}", r.code)
            }
        }
    }

    private fun parse(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw ApiException.Invalid("Invalid JSON response")
    }

    private fun parseTransportResult(body: String): TransportResult {
        val root = parse(body)
        return try {
            val items = if (root.has("value") && !root.isNull("value")) {
                val values = root.getJSONArray("value")
                (0 until values.length()).map { index ->
                    val item = values.getJSONObject(index)
                    val fields = buildMap {
                        item.keys().forEach { key ->
                            if (!item.isNull(key)) put(key, item.get(key).toString())
                        }
                    }
                    TransportItem(fields)
                }
            } else {
                emptyList()
            }
            TransportResult(
                success = root.optBoolean("success"),
                empty = root.optBoolean("empty"),
                retryable = root.optBoolean("retryable"),
                failureKind = root.optString("failureKind").takeIf { it.isNotBlank() && it != "null" },
                items = items,
                errorMessage = root.optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
            )
        } catch (e: JSONException) {
            throw ApiException.Invalid("Invalid transport response")
        }
    }

    private fun optEpochMillis(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        val raw = o.optString(key).trim()
        return if (raw.isBlank() || raw.equals("null", ignoreCase = true)) null else parseIsoInstant(raw)
    }

    private fun parseIsoInstant(raw: String): Long {
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (e: Exception) {
            throw ApiException.Invalid("Invalid timestamp in response")
        }
    }
}
