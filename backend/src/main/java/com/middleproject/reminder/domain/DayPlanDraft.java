package com.middleproject.reminder.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Conversation-owned, not-yet-persisted day-plan input. Null fields are allowed here so
 * the question engine can ask for exactly one missing value at a time.
 */
public record DayPlanDraft(LocalDate planDate, String timezone, String originName, String originAddress,
                           GeoPoint originCoordinates, List<ScheduleDraftItem> items,
                           Integer notificationLeadMinutes, boolean wakeAlarmRequested) {
    public DayPlanDraft {
        timezone = timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone.trim();
        items = items == null ? List.of() : List.copyOf(items);
    }
}
