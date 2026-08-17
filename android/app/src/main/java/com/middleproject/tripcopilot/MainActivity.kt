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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.middleproject.tripcopilot.data.DeviceApiClient
import com.middleproject.tripcopilot.ui.CompanionUiState
import com.middleproject.tripcopilot.ui.CompanionViewModel
import com.middleproject.tripcopilot.location.LocationQuality
import com.middleproject.tripcopilot.location.LocationQualityPolicy
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                loadCurrentLocation()
            } else {
                viewModel.locationUnavailable("Location permission denied; enter coordinates manually")
            }
        }

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
                        onSubwayArrivals = viewModel::realtimeSubwayArrivals,
                        onNearbyBusStops = viewModel::findNearbyBusStops,
                        onLandmarkBusStops = viewModel::findBusStopsByLandmark,
                        onBusArrivals = viewModel::busArrivals,
                        onExpressBus = viewModel::expressBusArrivals,
                        onIntercityBus = viewModel::intercityBusSchedule,
                        onUseCurrentLocation = ::requestCurrentLocation,
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

    private fun requestCurrentLocation() {
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (coarse || fine) loadCurrentLocation() else locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentLocation() {
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .setDurationMillis(15_000)
                .build()
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(request, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        viewModel.locationUnavailable("Fresh location unavailable; turn on precise location and retry")
                        return@addOnSuccessListener
                    }
                    when (LocationQualityPolicy().evaluate(System.currentTimeMillis(), location.time, location.accuracy)) {
                        LocationQuality.Accepted -> viewModel.findNearbyBusStops(location.latitude, location.longitude)
                        LocationQuality.Stale -> viewModel.locationUnavailable("Location is stale; move outdoors briefly and retry")
                        LocationQuality.Imprecise -> viewModel.locationUnavailable(
                            "Location accuracy is ${location.accuracy.toInt()} m; enable precise location and retry",
                        )
                    }
                }
                .addOnFailureListener {
                    viewModel.locationUnavailable("Current location request failed; enter a nearby landmark instead")
                }
        } catch (_: SecurityException) {
            viewModel.locationUnavailable("Location permission unavailable; enter coordinates manually")
        }
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
    onSubwayArrivals: (String) -> Unit,
    onNearbyBusStops: (Double, Double) -> Unit,
    onLandmarkBusStops: (String) -> Unit,
    onBusArrivals: (String, String) -> Unit,
    onExpressBus: (String, String) -> Unit,
    onIntercityBus: (String, String, String) -> Unit,
    onUseCurrentLocation: () -> Unit,
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
            onSubwayArrivals = onSubwayArrivals,
            onNearbyBusStops = onNearbyBusStops,
            onLandmarkBusStops = onLandmarkBusStops,
            onBusArrivals = onBusArrivals,
            onExpressBus = onExpressBus,
            onIntercityBus = onIntercityBus,
            onUseCurrentLocation = onUseCurrentLocation,
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
            enabled = !state.submitting,
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
    onSubwayArrivals: (String) -> Unit,
    onNearbyBusStops: (Double, Double) -> Unit,
    onLandmarkBusStops: (String) -> Unit,
    onBusArrivals: (String, String) -> Unit,
    onExpressBus: (String, String) -> Unit,
    onIntercityBus: (String, String, String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
        TransportCard(
            state = state.transport,
            onSubwayArrivals = onSubwayArrivals,
            onNearbyBusStops = onNearbyBusStops,
            onLandmarkBusStops = onLandmarkBusStops,
            onBusArrivals = onBusArrivals,
            onExpressBus = onExpressBus,
            onIntercityBus = onIntercityBus,
            onUseCurrentLocation = onUseCurrentLocation,
        )
        Spacer(Modifier.height(8.dp))
        if (state.trips.isEmpty() && state.reminders.isEmpty()) {
            Text("No trips or reminders yet. Pull refresh to sync.")
        }
        state.trips.forEach { trip ->
            TripCard(trip, onCancelTrip)
        }
        state.reminders.forEach { reminder ->
            ReminderCard(
                reminder = reminder,
                delivery = state.delivery[reminder.id].orEmpty(),
                onCancelReminder = onCancelReminder,
                onAckReminder = onAckReminder,
            )
        }
    }
}

@Composable
private fun TransportCard(
    state: com.middleproject.tripcopilot.ui.TransportUiState,
    onSubwayArrivals: (String) -> Unit,
    onNearbyBusStops: (Double, Double) -> Unit,
    onLandmarkBusStops: (String) -> Unit,
    onBusArrivals: (String, String) -> Unit,
    onExpressBus: (String, String) -> Unit,
    onIntercityBus: (String, String, String) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    var station by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var cityCode by remember { mutableStateOf("") }
    var nodeId by remember { mutableStateOf("") }
    var departureTerminal by remember { mutableStateOf("") }
    var arrivalTerminal by remember { mutableStateOf("") }
    var travelDate by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Public transport", style = MaterialTheme.typography.titleLarge)
            Text("Official public data · foreground location only", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(station, { station = it }, label = { Text("Subway station") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSubwayArrivals(station) }, enabled = !state.loading) { Text("Subway arrivals") }

            OutlinedTextField(
                landmark,
                { landmark = it },
                label = { Text("Nearby landmark (for example, Gangnam Station Exit 11)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onLandmarkBusStops(landmark) }, enabled = !state.loading) {
                Text("Find bus stops by place")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUseCurrentLocation, enabled = !state.loading) { Text("Use current location") }
                Button(
                    onClick = {
                        val lat = latitude.toDoubleOrNull()
                        val lon = longitude.toDoubleOrNull()
                        if (lat != null && lon != null) onNearbyBusStops(lat, lon)
                    },
                    enabled = !state.loading,
                ) { Text("Nearby stops") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cityCode, { cityCode = it }, label = { Text("City code") }, modifier = Modifier.weight(1f))
                OutlinedTextField(nodeId, { nodeId = it }, label = { Text("Bus stop ID") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = { onBusArrivals(cityCode, nodeId) }, enabled = !state.loading) { Text("Bus arrivals") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(departureTerminal, { departureTerminal = it }, label = { Text("From terminal") }, modifier = Modifier.weight(1f))
                OutlinedTextField(arrivalTerminal, { arrivalTerminal = it }, label = { Text("To terminal") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(travelDate, { travelDate = it }, label = { Text("Date (yyyyMMdd)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExpressBus(departureTerminal, arrivalTerminal) }, enabled = !state.loading) { Text("Express bus") }
                Button(onClick = { onIntercityBus(departureTerminal, arrivalTerminal, travelDate) }, enabled = !state.loading) { Text("Intercity bus") }
            }

            if (state.loading) CircularProgressIndicator(Modifier.padding(8.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { result ->
                val status = when {
                    result.success -> "Results"
                    result.empty -> "No matching services"
                    else -> "${result.failureKind ?: "Provider error"}${if (result.retryable) " · retryable" else ""}"
                }
                Text(status, style = MaterialTheme.typography.titleMedium)
                state.sourceLabel?.let { Text("Source: $it") }
                state.fetchedAtEpochMillis?.let { Text("Fetched: ${formatTime(it)}") }
                result.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                result.items.take(20).forEach { item ->
                    Text(item.fields.entries.joinToString(" · ") { "${it.key}: ${it.value}" })
                }
            }

            if (state.handoffs.isNotEmpty()) {
                Text("Official booking", style = MaterialTheme.typography.titleMedium)
                state.handoffs.toSortedMap().forEach { (name, url) ->
                    Button(onClick = { uriHandler.openUri(url) }) { Text(name) }
                }
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
