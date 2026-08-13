package com.middleproject.reminder;

import com.middleproject.reminder.infrastructure.scheduler.AwsSchedulerAdapter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsSchedulerAdapterTest {
    @Test void createContractAndConflictUpdateContract() {
        SchedulerClient client = mock(SchedulerClient.class);
        UUID id = UUID.randomUUID();
        OffsetDateTime due = OffsetDateTime.parse("2030-01-01T01:00:00Z");
        String payload = "{\"reminderId\":\"" + id + "\",\"schedulerVersion\":7,\"idempotencyKey\":\"" + id + ":7\"}";
        AwsSchedulerAdapter adapter = new AwsSchedulerAdapter(client, "phase6", "arn:role", "arn:queue", "Asia/Seoul");
        adapter.register(id, 7, due, payload);
        var create = org.mockito.ArgumentCaptor.forClass(CreateScheduleRequest.class);
        verify(client).createSchedule(create.capture());
        assertEquals("reminder-" + id, create.getValue().name());
        assertEquals("phase6", create.getValue().groupName());
        assertEquals("at(2030-01-01T10:00:00)", create.getValue().scheduleExpression());
        assertEquals("Asia/Seoul", create.getValue().scheduleExpressionTimezone());
        assertEquals(FlexibleTimeWindowMode.OFF, create.getValue().flexibleTimeWindow().mode());
        assertEquals("arn:queue", create.getValue().target().arn());
        assertEquals("arn:role", create.getValue().target().roleArn());
        assertEquals(payload, create.getValue().target().input());
        assertEquals(ActionAfterCompletion.DELETE, create.getValue().actionAfterCompletion());

        reset(client);
        when(client.createSchedule(any(CreateScheduleRequest.class))).thenThrow(ConflictException.builder().build());
        adapter.register(id, 7, due, payload);
        var update = org.mockito.ArgumentCaptor.forClass(UpdateScheduleRequest.class);
        verify(client).updateSchedule(update.capture());
        assertEquals("reminder-" + id, update.getValue().name());
        assertEquals("phase6", update.getValue().groupName());
        assertEquals("at(2030-01-01T10:00:00)", update.getValue().scheduleExpression());
        assertEquals("Asia/Seoul", update.getValue().scheduleExpressionTimezone());
        assertEquals(FlexibleTimeWindowMode.OFF, update.getValue().flexibleTimeWindow().mode());
        assertEquals("arn:queue", update.getValue().target().arn());
        assertEquals("arn:role", update.getValue().target().roleArn());
        assertEquals(payload, update.getValue().target().input());
        assertEquals(ActionAfterCompletion.DELETE, update.getValue().actionAfterCompletion());
        assertTrue(update.getValue().target().input().contains("schedulerVersion"));
        assertTrue(update.getValue().target().input().contains("idempotencyKey"));
    }

    @Test void missingScheduleCancelIsIdempotent() {
        SchedulerClient client = mock(SchedulerClient.class);
        when(client.deleteSchedule(any(DeleteScheduleRequest.class))).thenThrow(ResourceNotFoundException.builder().build());
        new AwsSchedulerAdapter(client, "phase6", "role", "queue", "Asia/Seoul").cancel(UUID.randomUUID(), 1);
        verify(client).deleteSchedule(any(DeleteScheduleRequest.class));
    }
}
