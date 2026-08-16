package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections for the Android companion, free of web-layer SQL. */
public interface DeviceReminderProjectionRepository {

    /** The trip owning the reminder, when it exists. */
    Optional<UUID> tripId(UUID reminderId);

    /** Event start minus policy lead minutes; the instant an Android alarm should fire. */
    Optional<OffsetDateTime> alarmTime(UUID reminderId);
}
