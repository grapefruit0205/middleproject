package com.middleproject.reminder.domain;

public record PlaceSearchRequest(String destination, PlaceCategory category) {

    private static final int MAX_DESTINATION_LENGTH = 200;

    public PlaceSearchRequest {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be null or blank");
        }
        if (destination.length() > MAX_DESTINATION_LENGTH) {
            throw new IllegalArgumentException(
                    "destination must not exceed " + MAX_DESTINATION_LENGTH + " characters");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
    }
}
