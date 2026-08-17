package com.middleproject.reminder.transport.domain;

public record LandmarkCandidate(String name, String address, double latitude, double longitude) {
    public LandmarkCandidate {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("landmark name must not be blank");
        if (address == null) address = "";
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            throw new IllegalArgumentException("landmark coordinates must be within South Korea");
        }
    }
}
