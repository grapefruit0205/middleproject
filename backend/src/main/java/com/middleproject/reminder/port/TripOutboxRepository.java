package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TripOutboxRepository {
    void insert(UUID id, UUID tripId, String operation, long expectedVersion, long schedulerVersion,
                OffsetDateTime dueAt, String payload);
}
