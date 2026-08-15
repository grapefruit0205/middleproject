package com.middleproject.reminder.domain;

import java.util.UUID;

public record FollowUpConsent(UUID tripId, ConsentStatus status, long version) {

    public FollowUpConsent {
        if (tripId == null) {
            throw new IllegalArgumentException("tripId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
