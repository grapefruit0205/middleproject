package com.middleproject.reminder.transport.domain;

/** A station candidate discovered from an explicit foreground location or saved place. */
public record NearbySubwayStation(
        String name,
        String address,
        double latitude,
        double longitude,
        Integer distanceMeters
) {
    public NearbySubwayStation {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("station name must not be blank");
        if (address == null) address = "";
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            throw new IllegalArgumentException("station coordinates must be within South Korea");
        }
        if (distanceMeters != null && distanceMeters < 0) throw new IllegalArgumentException("distance must not be negative");
    }
}
