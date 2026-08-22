package com.middleproject.reminder.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record DayPlan(UUID id, String ownerId, LocalDate planDate, String timezone,
                      DayPlanStatus status, long version) {
    public DayPlan {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        if (planDate == null) throw new IllegalArgumentException("planDate is required");
        if (timezone == null || timezone.isBlank()) throw new IllegalArgumentException("timezone is required");
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("timezone is invalid", e);
        }
        if (status == null) throw new IllegalArgumentException("status is required");
        if (version < 0) throw new IllegalArgumentException("version must be nonnegative");
    }

    public DayPlan propose() { return transition(DayPlanStatus.PROPOSED); }
    public DayPlan confirm() { return transition(DayPlanStatus.CONFIRMED); }
    public DayPlan activate() { return transition(DayPlanStatus.ACTIVE); }
    public DayPlan complete() { return transition(DayPlanStatus.COMPLETED); }
    public DayPlan cancel() { return transition(DayPlanStatus.CANCELLED); }

    private DayPlan transition(DayPlanStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid day plan transition: " + status + " -> " + target);
        }
        return new DayPlan(id, ownerId, planDate, timezone, target, version + 1);
    }
}
