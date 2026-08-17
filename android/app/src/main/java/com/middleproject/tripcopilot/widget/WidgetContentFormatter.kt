package com.middleproject.tripcopilot.widget

import com.middleproject.tripcopilot.data.DeviceApiClient

object WidgetContentFormatter {
    fun format(title: String, items: List<DeviceApiClient.TransportItem>): String {
        if (items.isEmpty()) return "$title\nNo arrival information"
        val lines = items.take(3).map { item ->
            val fields = item.fields
            val message = fields["arrivalMessage"] ?: fields["arrivalMessage2"]
            if (!message.isNullOrBlank()) {
                listOfNotNull(message, fields["destinationName"]).joinToString(" · ")
            } else {
                val seconds = fields["arrivalSeconds"]?.toIntOrNull()
                val minutes = seconds?.let { maxOf(1, (it + 59) / 60) }
                val stops = fields["remainingStops"]?.toIntOrNull()
                listOfNotNull(minutes?.let { "${it}분" }, stops?.let { "${it}정류장" })
                    .ifEmpty { listOf("Arrival data received") }
                    .joinToString(" · ")
            }
        }
        return (listOf(title) + lines).joinToString("\n")
    }
}
