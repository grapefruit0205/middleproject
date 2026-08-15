package com.middleproject.reminder.domain;

public record GeoPoint(double latitude, double longitude) {
    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("coordinates out of range");
        }
    }
}
