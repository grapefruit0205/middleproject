package com.middleproject.tripcopilot.data

/** Shared deterministic [DeviceRefreshBackend] fake for JVM repository tests. */
class FakeDeviceRefreshBackend : DeviceRefreshBackend {
    var deleteFcmTokenCalled = false
    var exchangeCalled = false
    private val recordedLabels = mutableListOf<String>()

    override fun hasValidCredential(): Boolean = true

    override fun exchange(pairingCode: String, installationId: String, label: String): DeviceApiClient.ExchangeResult {
        exchangeCalled = true
        recordedLabels += label
        return DeviceApiClient.ExchangeResult("token-1", "device-1", expiresAtEpochMillis = 2_000L)
    }

    override fun trips(): List<DeviceApiClient.TripView> = emptyList()

    override fun reminders(): List<DeviceApiClient.ReminderView> = emptyList()

    override fun delivery(reminderId: String): List<DeviceApiClient.DeliveryView> = emptyList()

    override fun cancelTrip(tripId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun cancelReminder(reminderId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun ackReminder(reminderId: String, expectedVersion: Long) = throw UnsupportedOperationException()

    override fun registerFcmToken(registrationToken: String) = throw UnsupportedOperationException()

    override fun deleteFcmToken() {
        deleteFcmTokenCalled = true
    }
}
