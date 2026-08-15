package com.middleproject.reminder.domain;

public enum TripStatus {
    DRAFT, AWAITING_CONFIRMATION, CONFIRMED, CANCELLED, EXPIRED;

    public boolean canTransitionTo(TripStatus target) {
        return switch (this) {
            case DRAFT -> target == AWAITING_CONFIRMATION || target == CANCELLED || target == EXPIRED;
            case AWAITING_CONFIRMATION -> target == CONFIRMED || target == CANCELLED || target == EXPIRED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED, EXPIRED -> target == DRAFT;
        };
    }
}
