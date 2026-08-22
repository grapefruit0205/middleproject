package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;

/** A partial schedule item collected by the conversational planning flow. */
public record ScheduleDraftItem(String title, ScheduleTimeType timeType,
                                OffsetDateTime startsAt, OffsetDateTime endsAt,
                                Integer durationMinutes, String placeName, String address,
                                GeoPoint coordinates, String travelMode) {
}
