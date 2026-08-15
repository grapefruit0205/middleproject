package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;

/** Immutable plan produced by a route provider for one origin/destination/departure request. */
public record RoutePlan(GeoPoint originPoint, GeoPoint destinationPoint, int distanceMeters,
                        int baseDurationMinutes, int trafficDurationMinutes, int tollAmount,
                        String provider, String source) {

    public RoutePlan {
        if (distanceMeters < 0) throw new IllegalArgumentException("distanceMeters must be nonnegative");
        if (baseDurationMinutes < 0) throw new IllegalArgumentException("baseDurationMinutes must be nonnegative");
        if (trafficDurationMinutes < baseDurationMinutes) {
            throw new IllegalArgumentException("trafficDurationMinutes must be >= baseDurationMinutes");
        }
        if (tollAmount < 0) throw new IllegalArgumentException("tollAmount must be nonnegative");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
    }
}
