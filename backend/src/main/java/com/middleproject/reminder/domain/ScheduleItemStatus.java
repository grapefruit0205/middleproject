package com.middleproject.reminder.domain;

public enum ScheduleItemStatus {
    PLANNED, ACTIVE, COMPLETED, CANCELLED;

    public boolean canTransitionTo(ScheduleItemStatus target) {
        if (target == null) return false;
        return switch (this) {
            case PLANNED -> target == ACTIVE || target == COMPLETED || target == CANCELLED;
            case ACTIVE -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
