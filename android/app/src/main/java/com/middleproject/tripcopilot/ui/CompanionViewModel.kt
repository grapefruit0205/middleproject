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
    data class Pairing(val message: String? = null) : CompanionUiState
    data class Paired(
        val trips: List<DeviceApiClient.TripView>,
        val reminders: List<DeviceApiClient.ReminderView>,
        val delivery: Map<String, List<DeviceApiClient.DeliveryView>> = emptyMap(),
        val loading: Boolean = false,
        val error: String? = null,
        val degradedAlarm: Boolean = false,
    ) : CompanionUiState
}

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

    init {
        refresh()
    }

    fun submitPairingCode(rawCode: String, label: String) {
        val code = PairingCode.canonical(rawCode)
        if (code == null) {
            _state.value = CompanionUiState.Pairing("Enter a valid 10-character pairing code (XXXXX-XXXXX)")
            return
        }
        if (label.isBlank() || label.length > 200) {
            _state.value = CompanionUiState.Pairing("Device label must be 1-200 characters")
            return
        }
        viewModelScope.launch {
            _state.value = CompanionUiState.Pairing("Pairing…")
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

    private fun setError(message: String) {
        val current = _state.value
        if (current is CompanionUiState.Paired) {
            _state.value = current.copy(error = message)
        }
    }
}
