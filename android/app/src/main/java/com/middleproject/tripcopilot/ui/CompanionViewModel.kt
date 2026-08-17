package com.middleproject.tripcopilot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.data.DeviceRepository
import com.middleproject.tripcopilot.domain.PairingCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CompanionUiState {
    data object Loading : CompanionUiState
    data class Pairing(val message: String? = null, val submitting: Boolean = false) : CompanionUiState
    data class Paired(
        val trips: List<DeviceApiClient.TripView>,
        val reminders: List<DeviceApiClient.ReminderView>,
        val delivery: Map<String, List<DeviceApiClient.DeliveryView>> = emptyMap(),
        val loading: Boolean = false,
        val error: String? = null,
        val degradedAlarm: Boolean = false,
        val transport: TransportUiState = TransportUiState(),
    ) : CompanionUiState
}

data class TransportUiState(
    val loading: Boolean = false,
    val result: DeviceApiClient.TransportResult? = null,
    val handoffs: Map<String, String> = emptyMap(),
    val error: String? = null,
    val sourceLabel: String? = null,
    val fetchedAtEpochMillis: Long? = null,
)

/**
 * State holder for pairing, loading, paired trip/reminder/delivery views,
 * empty/error/degraded-alarm state, cancel/ACK, refresh, and disconnect.
 */
class CompanionViewModel(
    private val repository: DeviceRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<CompanionUiState>(CompanionUiState.Loading)
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()
    private var pairingInProgress = false

    init {
        refresh()
    }

    fun submitPairingCode(rawCode: String, label: String) {
        if (pairingInProgress) return
        val code = PairingCode.canonical(rawCode)
        if (code == null) {
            _state.value = CompanionUiState.Pairing("Enter a valid 10-character pairing code (XXXXX-XXXXX)")
            return
        }
        if (label.isBlank() || label.length > 200) {
            _state.value = CompanionUiState.Pairing("Device label must be 1-200 characters")
            return
        }
        pairingInProgress = true
        _state.value = CompanionUiState.Pairing("Pairing…", submitting = true)
        viewModelScope.launch {
            try {
                repository.exchange(code, label)
                refresh()
            } catch (e: DeviceApiClient.ApiException.Network) {
                _state.value = CompanionUiState.Pairing("Cannot reach the server: ${e.message}")
            } catch (e: DeviceApiClient.ApiException.Unauthorized) {
                _state.value = CompanionUiState.Pairing("Pairing code rejected or expired")
            } catch (e: DeviceApiClient.ApiException.Conflict) {
                _state.value = CompanionUiState.Pairing("Pairing conflict: a different device is already paired")
            } catch (e: Exception) {
                _state.value = CompanionUiState.Pairing("Pairing failed: ${e.message}")
            } finally {
                pairingInProgress = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            if (current is CompanionUiState.Paired) {
                _state.value = current.copy(loading = true, error = null)
            }
            try {
                val result = repository.refresh()
                _state.value = CompanionUiState.Paired(
                    trips = result.trips,
                    reminders = result.reminders,
                    delivery = result.delivery,
                    degradedAlarm = result.degradedAlarm,
                    transport = TransportUiState(
                        handoffs = runCatching { repository.transportHandoffs() }.getOrDefault(emptyMap()),
                    ),
                )
            } catch (e: DeviceApiClient.ApiException.Unauthorized) {
                _state.value = CompanionUiState.Pairing("Session expired; pair again")
            } catch (e: Exception) {
                val fallback = _state.value
                _state.value = if (fallback is CompanionUiState.Paired) {
                    fallback.copy(loading = false, error = "Refresh failed: ${e.message}")
                } else {
                    CompanionUiState.Pairing("Cannot load data: ${e.message}")
                }
            }
        }
    }

    fun cancelTrip(tripId: String, expectedVersion: Long) {
        viewModelScope.launch {
            try {
                repository.cancelTrip(tripId, expectedVersion)
                refresh()
            } catch (e: Exception) {
                setError("Cancel trip failed: ${e.message}")
            }
        }
    }

    fun cancelReminder(reminderId: String, expectedVersion: Long) {
        viewModelScope.launch {
            try {
                repository.cancelReminder(reminderId, expectedVersion)
                refresh()
            } catch (e: Exception) {
                setError("Cancel reminder failed: ${e.message}")
            }
        }
    }

    fun ackReminder(reminderId: String, expectedVersion: Long) {
        viewModelScope.launch {
            try {
                repository.ackReminder(reminderId, expectedVersion)
                refresh()
            } catch (e: Exception) {
                setError("Acknowledge failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                val serverOk = repository.disconnect()
                _state.value = CompanionUiState.Pairing(
                    if (serverOk) "Disconnected" else "Disconnected locally; server disconnect failed"
                )
            } catch (e: Exception) {
                _state.value = CompanionUiState.Pairing("Disconnect failed: ${e.message}")
            }
        }
    }

    fun realtimeSubwayArrivals(stationName: String) {
        if (stationName.isBlank()) return setTransportError("Enter a subway station name")
        launchTransport("Seoul Open Data realtime subway") { repository.realtimeSubwayArrivals(stationName) }
    }

    fun findNearbyBusStops(latitude: Double, longitude: Double) {
        if (latitude !in 33.0..39.0 || longitude !in 124.0..132.0) {
            return setTransportError("Location must be within South Korea")
        }
        launchTransport("MOLIT TAGO nearby bus stops") { repository.nearbyBusStops(latitude, longitude) }
    }

    fun findBusStopsByLandmark(landmark: String) {
        if (landmark.isBlank()) return setTransportError("Enter a nearby building, station exit, or landmark")
        launchTransport("Kakao Local + MOLIT TAGO bus stops") { repository.busStopsByLandmark(landmark) }
    }

    fun busArrivals(cityCode: String, nodeId: String) {
        val city = cityCode.toIntOrNull()
        if (city == null || city < 1 || nodeId.isBlank()) {
            return setTransportError("Enter a positive city code and bus stop ID")
        }
        launchTransport("MOLIT TAGO bus arrivals") { repository.busArrivals(city, nodeId) }
    }

    fun expressBusArrivals(departureTerminal: String, arrivalTerminal: String) {
        if (departureTerminal.isBlank() || arrivalTerminal.isBlank()) {
            return setTransportError("Enter departure and arrival terminal codes")
        }
        launchTransport("MOLIT TAGO express bus arrivals") {
            repository.expressBusArrivals(departureTerminal, arrivalTerminal)
        }
    }

    fun intercityBusSchedule(departureTerminal: String, arrivalTerminal: String, date: String) {
        if (departureTerminal.isBlank() || arrivalTerminal.isBlank() || !date.matches(Regex("[0-9]{8}"))) {
            return setTransportError("Enter terminal IDs and date as yyyyMMdd")
        }
        launchTransport("MOLIT TAGO intercity bus schedule") {
            repository.intercityBusSchedule(departureTerminal, arrivalTerminal, date)
        }
    }

    fun locationUnavailable(message: String) = setTransportError(message)

    private fun launchTransport(sourceLabel: String, block: suspend () -> DeviceApiClient.TransportResult) {
        val current = _state.value as? CompanionUiState.Paired ?: return
        _state.value = current.copy(transport = current.transport.copy(loading = true, error = null))
        viewModelScope.launch {
            try {
                val result = block()
                updateTransport {
                    it.copy(
                        loading = false,
                        result = result,
                        error = null,
                        sourceLabel = sourceLabel,
                        fetchedAtEpochMillis = clockMillis(),
                    )
                }
            } catch (e: DeviceApiClient.ApiException.Unauthorized) {
                _state.value = CompanionUiState.Pairing("Session expired; pair again")
            } catch (e: Exception) {
                updateTransport { it.copy(loading = false, error = "Transport lookup failed: ${e.message}") }
            }
        }
    }

    private fun setTransportError(message: String) {
        updateTransport { it.copy(loading = false, error = message) }
    }

    private fun updateTransport(update: (TransportUiState) -> TransportUiState) {
        val current = _state.value as? CompanionUiState.Paired ?: return
        _state.value = current.copy(transport = update(current.transport))
    }

    private fun setError(message: String) {
        val current = _state.value
        if (current is CompanionUiState.Paired) {
            _state.value = current.copy(error = message)
        }
    }
}
