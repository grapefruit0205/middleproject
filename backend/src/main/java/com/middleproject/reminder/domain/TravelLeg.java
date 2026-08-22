package com.middleproject.reminder.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TravelLeg(UUID id, UUID dayPlanId, UUID fromItemId, UUID toItemId, String mode,
                        int durationMinutes, int bufferMinutes, OffsetDateTime departureAt,
                        OffsetDateTime arrivalAt, String provider, String source, Instant fetchedAt,
                        int sequence, long version) {
    public TravelLeg {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (dayPlanId == null) throw new IllegalArgumentException("dayPlanId is required");
        if (toItemId == null) throw new IllegalArgumentException("toItemId is required");
        if (mode == null || mode.isBlank()) throw new IllegalArgumentException("mode is required");
        if (durationMinutes < 0) throw new IllegalArgumentException("durationMinutes must be nonnegative");
        if (bufferMinutes < 0) throw new IllegalArgumentException("bufferMinutes must be nonnegative");
        if (departureAt == null || arrivalAt == null) throw new IllegalArgumentException("leg times are required");
        if (arrivalAt.isBefore(departureAt)) throw new IllegalArgumentException("arrivalAt must not be before departureAt");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        if (fetchedAt == null) throw new IllegalArgumentException("fetchedAt is required");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be nonnegative");
        if (version < 0) throw new IllegalArgumentException("version must be nonnegative");
    }
}
