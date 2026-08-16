package com.middleproject.tripcopilot

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.ui.CompanionUiState
import com.middleproject.tripcopilot.ui.CompanionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: CompanionViewModel by viewModels {
        CompanionViewModelFactory((application as TripCopilotApplication).repository)
    }

    // androidx.activity 1.10.1 pulls fragment >= 1.3 transitively; this lint check
    // misreads the transitive fragment version for ComponentActivity-only apps.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result is informational */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    CompanionScreen(
                        state = state,
                        onPair = viewModel::submitPairingCode,
                        onRefresh = viewModel::refresh,
                        onCancelTrip = viewModel::cancelTrip,
                        onCancelReminder = viewModel::cancelReminder,
                        onAckReminder = viewModel::ackReminder,
                        onDisconnect = viewModel::disconnect,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun CompanionScreen(
    state: CompanionUiState,
    onPair: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCancelTrip: (String, Long) -> Unit,
    onCancelReminder: (String, Long) -> Unit,
    onAckReminder: (String, Long) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CompanionUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        }

        is CompanionUiState.Pairing -> PairingScreen(state, onPair, modifier)

        is CompanionUiState.Paired -> PairedScreen(
            state = state,
            onRefresh = onRefresh,
            onCancelTrip = onCancelTrip,
            onCancelReminder = onCancelReminder,
            onAckReminder = onAckReminder,
            onDisconnect = onDisconnect,
            modifier = modifier,
        )
    }
}

@Composable
private fun PairingScreen(
    state: CompanionUiState.Pairing,
    onPair: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Trip Copilot", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Pairing code (XXXXX-XXXXX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Device label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onPair(code, label) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pair")
        }
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PairedScreen(
    state: CompanionUiState.Paired,
    onRefresh: () -> Unit,
    onCancelTrip: (String, Long) -> Unit,
    onCancelReminder: (String, Long) -> Unit,
    onAckReminder: (String, Long) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
            Button(onClick = onDisconnect) { Text("Disconnect") }
        }
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(8.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (state.degradedAlarm) {
            Text(
                "Exact alarms unavailable; using inexact scheduling.",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.trips.isEmpty() && state.reminders.isEmpty()) {
            Text("No trips or reminders yet. Pull refresh to sync.")
        }
        LazyColumn {
            items(state.trips) { trip ->
                TripCard(trip, onCancelTrip)
            }
            items(state.reminders) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    delivery = state.delivery[reminder.id].orEmpty(),
                    onCancelReminder = onCancelReminder,
                    onAckReminder = onAckReminder,
                )
            }
        }
    }
}

@Composable
private fun TripCard(trip: DeviceApiClient.TripView, onCancelTrip: (String, Long) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${trip.departure} → ${trip.destination}", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${trip.status}")
            trip.departureAtEpochMillis?.let {
                Text("Departure: ${formatTime(it)}")
            }
            if (trip.status == "CONFIRMED" || trip.status == "DRAFT") {
                Button(onClick = { onCancelTrip(trip.id, trip.version) }) { Text("Cancel trip") }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: DeviceApiClient.ReminderView,
    delivery: List<DeviceApiClient.DeliveryView>,
    onCancelReminder: (String, Long) -> Unit,
    onAckReminder: (String, Long) -> Unit,
) {
    val active = reminder.status !in setOf("CANCELLED", "ACKNOWLEDGED", "DELIVERY_FAILED", "SCHEDULE_FAILED")
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Reminder ${reminder.status}", style = MaterialTheme.typography.titleMedium)
            reminder.alarmTimeEpochMillis?.let {
                Text("Alarm: ${formatTime(it)}")
            }
            if (delivery.isNotEmpty()) {
                Text("Delivery:", style = MaterialTheme.typography.labelMedium)
                delivery.forEach { entry ->
                    val attempted = entry.attemptedAtEpochMillis?.let { formatTime(it) } ?: "—"
                    Text("  ${entry.channel}: ${entry.status} ($attempted)")
                }
            }
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAckReminder(reminder.id, reminder.version) }) { Text("Acknowledge") }
                    Button(onClick = { onCancelReminder(reminder.id, reminder.version) }) { Text("Cancel") }
                }
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis))
