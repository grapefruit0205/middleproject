package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;

/** Immutable request for a route plan, independent of how the provider is invoked. */
public record RoutePlanRequest(String origin, String destination, GeoPoint originPoint,
                               GeoPoint destinationPoint, OffsetDateTime departureAt) {
}
