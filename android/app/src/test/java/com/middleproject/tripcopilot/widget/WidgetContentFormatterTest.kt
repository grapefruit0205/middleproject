package com.middleproject.tripcopilot.widget

import com.middleproject.tripcopilot.data.DeviceApiClient
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentFormatterTest {
    @Test
    fun `formats subway arrival and bus arrival without internal ids`() {
        val subway = WidgetContentFormatter.format("회사역", listOf(
            DeviceApiClient.TransportItem(mapOf("arrivalMessage" to "2분 후", "destinationName" to "성수행")),
        ))
        val bus = WidgetContentFormatter.format("회사 앞 341", listOf(
            DeviceApiClient.TransportItem(mapOf("arrivalSeconds" to "180", "remainingStops" to "2")),
        ))

        assertTrue(subway.contains("2분 후"))
        assertTrue(bus.contains("3분"))
        assertTrue(bus.contains("2정류장"))
    }
}
