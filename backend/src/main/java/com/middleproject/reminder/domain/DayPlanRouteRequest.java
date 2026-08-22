package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;

/** Provider-neutral request for one read-only day-plan travel estimate. */
public record DayPlanRouteRequest(String originName, GeoPoint originCoordinates,
                                  String destinationName, GeoPoint destinationCoordinates,
                                  String mode, OffsetDateTime requestedArrivalAt) {
    public DayPlanRouteRequest {
        if (originName == null || originName.isBlank()) throw new IllegalArgumentException("originName is required");
        if (originCoordinates == null) throw new IllegalArgumentException("originCoordinates are required");
        if (destinationName == null || destinationName.isBlank()) throw new IllegalArgumentException("destinationName is required");
        if (destinationCoordinates == null) throw new IllegalArgumentException("destinationCoordinates are required");
        if (mode == null || mode.isBlank()) throw new IllegalArgumentException("mode is required");
        if (requestedArrivalAt == null) throw new IllegalArgumentException("requestedArrivalAt is required");
    }
}
