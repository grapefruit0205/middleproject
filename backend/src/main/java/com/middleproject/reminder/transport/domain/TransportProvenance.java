package com.middleproject.reminder.transport.domain;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public record TransportProvenance(
        String sourceName,
        String endpoint,
        boolean realtime,
        OffsetDateTime fetchedAt
) {
    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public TransportProvenance {
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        fetchedAt = fetchedAt.atZoneSameInstant(SEOUL_ZONE).toOffsetDateTime();
    }
}
