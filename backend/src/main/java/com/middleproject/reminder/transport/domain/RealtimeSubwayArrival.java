package com.middleproject.reminder.transport.domain;

import java.time.OffsetDateTime;

public record RealtimeSubwayArrival(
        String stationName,
        String lineName,
        String destinationName,
        String directionMessage,
        String arrivalMessage,
        Integer arrivalSeconds,
        OffsetDateTime receivedAt
) {}
