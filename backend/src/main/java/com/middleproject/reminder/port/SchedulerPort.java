package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SchedulerPort {
    void register(UUID reminderId, long schedulerVersion, OffsetDateTime dueAt, String payload);
    void cancel(UUID reminderId, long schedulerVersion);
}
