package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record ScheduleItem(UUID id, UUID dayPlanId, String title, ScheduleTimeType timeType,
                           OffsetDateTime startsAt, OffsetDateTime endsAt, int durationMinutes,
                           String placeName, String address, GeoPoint coordinates, int sequence,
                           ScheduleItemStatus status, long version) {
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Seoul");

    public ScheduleItem {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (dayPlanId == null) throw new IllegalArgumentException("dayPlanId is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (timeType == null) throw new IllegalArgumentException("timeType is required");
        if (startsAt == null && timeType != ScheduleTimeType.FLEXIBLE) {
            throw new IllegalArgumentException("startsAt is required for a fixed or deadline item");
        }
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("endsAt must not be before startsAt");
        }
        if (startsAt != null && !startsAt.atZoneSameInstant(USER_ZONE).toLocalDate().equals(
                endsAt == null ? startsAt.atZoneSameInstant(USER_ZONE).toLocalDate() : endsAt.atZoneSameInstant(USER_ZONE).toLocalDate())) {
            throw new IllegalArgumentException("schedule item must stay on one Asia/Seoul date");
        }
        if (durationMinutes < 0) throw new IllegalArgumentException("durationMinutes must be nonnegative");
        if (placeName == null || placeName.isBlank()) throw new IllegalArgumentException("placeName is required");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be nonnegative");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (version < 0) throw new IllegalArgumentException("version must be nonnegative");
    }

    public ScheduleItem activate() { return transition(ScheduleItemStatus.ACTIVE); }
    public ScheduleItem complete() { return transition(ScheduleItemStatus.COMPLETED); }
    public ScheduleItem cancel() { return transition(ScheduleItemStatus.CANCELLED); }

    private ScheduleItem transition(ScheduleItemStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid schedule item transition: " + status + " -> " + target);
        }
        return new ScheduleItem(id, dayPlanId, title, timeType, startsAt, endsAt, durationMinutes,
                placeName, address, coordinates, sequence, target, version + 1);
    }
}
