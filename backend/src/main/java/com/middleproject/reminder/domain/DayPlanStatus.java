package com.middleproject.reminder.domain;

public enum DayPlanStatus {
    DRAFT, PROPOSED, CONFIRMED, ACTIVE, COMPLETED, CANCELLED;

    public boolean canTransitionTo(DayPlanStatus target) {
        if (target == null) return false;
        return switch (this) {
            case DRAFT -> target == PROPOSED || target == CANCELLED;
            case PROPOSED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == ACTIVE || target == CANCELLED;
            case ACTIVE -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
