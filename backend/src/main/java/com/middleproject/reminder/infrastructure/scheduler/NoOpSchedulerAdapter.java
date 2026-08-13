package com.middleproject.reminder.infrastructure.scheduler;

import com.middleproject.reminder.port.SchedulerPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "scheduler.aws.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSchedulerAdapter implements SchedulerPort {
    public void register(UUID reminderId, long schedulerVersion, OffsetDateTime dueAt, String payload) { }
    public void cancel(UUID reminderId, long schedulerVersion) { }
}
