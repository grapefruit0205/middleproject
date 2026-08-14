package com.middleproject.reminder.domain;

public enum ReminderStatus {
    CREATED, SCHEDULE_PENDING, SCHEDULED, DISPATCHED, DELIVERED, ACKNOWLEDGED,
    SCHEDULE_FAILED, DELIVERY_FAILED, RETRYING, CANCELLED;

    public boolean canTransitionTo(ReminderStatus target) {
        return switch (this) {
            case CREATED -> target == SCHEDULE_PENDING || target == CANCELLED;
            case SCHEDULE_PENDING -> target == SCHEDULED || target == SCHEDULE_FAILED || target == CANCELLED;
            case SCHEDULED -> target == DISPATCHED || target == SCHEDULE_FAILED || target == RETRYING || target == CANCELLED;
            case DISPATCHED -> target == DELIVERED || target == DELIVERY_FAILED || target == RETRYING;
            case DELIVERED -> target == ACKNOWLEDGED;
            case SCHEDULE_FAILED, DELIVERY_FAILED -> target == RETRYING || target == CANCELLED;
            case RETRYING -> target == SCHEDULE_PENDING || target == SCHEDULED || target == DELIVERED || target == DELIVERY_FAILED || target == RETRYING || target == CANCELLED;
            case ACKNOWLEDGED, CANCELLED -> false;
        };
    }
}
