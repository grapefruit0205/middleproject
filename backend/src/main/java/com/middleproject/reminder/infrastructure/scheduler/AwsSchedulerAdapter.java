package com.middleproject.reminder.infrastructure.scheduler;

import com.middleproject.reminder.port.SchedulerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "scheduler.aws.enabled", havingValue = "true")
public class AwsSchedulerAdapter implements SchedulerPort {
    private final SchedulerClient scheduler;
    private final String group, roleArn, queueArn, timezone;
    public AwsSchedulerAdapter(SchedulerClient scheduler, @Value("${scheduler.group:default}") String group, @Value("${scheduler.role-arn:}") String roleArn, @Value("${scheduler.queue-arn:}") String queueArn, @Value("${scheduler.timezone:Asia/Seoul}") String timezone) { this.scheduler=scheduler; this.group=group; this.roleArn=roleArn; this.queueArn=queueArn; this.timezone=timezone; }

    public void register(UUID id, long version, OffsetDateTime dueAt, String payload) {
        String name=name(id);
        String expression = "at(" + dueAt.atZoneSameInstant(ZoneId.of(timezone)).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + ")";
        CreateScheduleRequest r=CreateScheduleRequest.builder().name(name).groupName(group).scheduleExpression(expression).scheduleExpressionTimezone(timezone).flexibleTimeWindow(FlexibleTimeWindow.builder().mode(FlexibleTimeWindowMode.OFF).build()).target(Target.builder().arn(queueArn).roleArn(roleArn).input(payload).build()).actionAfterCompletion(ActionAfterCompletion.DELETE).build();
        try { scheduler.createSchedule(r); } catch (ConflictException e) { scheduler.updateSchedule(UpdateScheduleRequest.builder().name(name).groupName(group).scheduleExpression(r.scheduleExpression()).scheduleExpressionTimezone(timezone).flexibleTimeWindow(r.flexibleTimeWindow()).target(r.target()).actionAfterCompletion(ActionAfterCompletion.DELETE).build()); }
    }
    public void cancel(UUID id, long version) { try { scheduler.deleteSchedule(DeleteScheduleRequest.builder().name(name(id)).groupName(group).build()); } catch (ResourceNotFoundException ignored) {} }
    private String name(UUID id) { return "reminder-" + id; }
}
