package com.middleproject.reminder;

import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.application.ReminderDeliveryConsumer;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.port.NotificationSender;
import com.middleproject.reminder.port.NotificationTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {ReminderPlatformApplication.class, NotificationPersistenceIntegrationTest.Configuration.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "notification.email.enabled=false", "notification.push.enabled=false"
})
class NotificationPersistenceIntegrationTest {
    @Autowired EventService events;
    @Autowired PolicyService policies;
    @Autowired ReminderService reminders;
    @Autowired SchedulerOutboxService outbox;
    @Autowired ReminderDeliveryService acceptance;
    @Autowired NotificationDeliveryService notifications;
    @Autowired JdbcTemplate db;
    @Autowired ProbeSender sender;

    @BeforeEach
    void clean() {
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        sender.reset();
    }
    @Test
    void successPersistsAttemptProviderResponseAndDeliveryState() {
        Fixture fixture = fixture("EMAIL");
        NotificationDeliveryService.AttemptResult result = notifications.deliver(fixture.payload());
        assertEquals("SUCCEEDED", result.status());
        assertEquals("provider-1", result.providerMessageId());
        assertNotNull(result.correlationId());
        assertEquals(1, sender.calls());
        assertEquals("DELIVERED", reminderStatus(fixture.reminderId()));
        assertEquals(1, db.queryForObject("select count(*) from notification_attempt", Integer.class));
        assertEquals("SUCCEEDED", db.queryForObject("select status from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals("EMAIL", db.queryForObject("select channel from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals(fixture.reminderId() + ":1", db.queryForObject("select delivery_key from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertNull(db.queryForObject("select active_delivery_key from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals("provider-1", db.queryForObject("select provider_message_id from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertNotNull(db.queryForObject("select completed_at from notification_attempt where correlation_id=?", OffsetDateTime.class, result.correlationId()));
    }
    @Test
    void terminalProviderFailurePersistsFailureAndGuardedTransition() {
        sender.failNext(new IllegalStateException("rejected"));
        Fixture fixture = fixture("EMAIL");
        NotificationDeliveryService.AttemptResult result = notifications.deliver(fixture.payload());
        assertEquals("PROVIDER_FAILURE", result.status());
        assertFalse(result.retryable());
        assertEquals("DELIVERY_FAILED", reminderStatus(fixture.reminderId()));
        assertEquals("PROVIDER_FAILURE", db.queryForObject("select error_classification from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals("rejected", db.queryForObject("select error_message from notification_attempt where correlation_id=?", String.class, result.correlationId()));
    }
    @Test
    void timeoutRetryIsPersistedAndCanDeliverLater() {
        sender.failNext(new NotificationTimeoutException("slow SES", null));
        Fixture fixture = fixture("EMAIL");
        NotificationDeliveryService.AttemptResult first = notifications.deliver(fixture.payload());
        assertEquals("RETRYABLE_TIMEOUT", first.status());
        assertTrue(first.retryable());
        assertEquals("RETRYING", reminderStatus(fixture.reminderId()));
        NotificationDeliveryService.AttemptResult second = notifications.deliver(fixture.payload());
        assertEquals("SUCCEEDED", second.status());
        assertEquals("DELIVERED", reminderStatus(fixture.reminderId()));
        assertEquals(2, sender.calls());
        assertEquals(2, db.queryForObject("select count(*) from notification_attempt", Integer.class));
        assertEquals("RETRYABLE_TIMEOUT", db.queryForObject("select error_classification from notification_attempt where correlation_id=?", String.class, first.correlationId()));
        assertEquals("SUCCEEDED", db.queryForObject("select status from notification_attempt where correlation_id=?", String.class, second.correlationId()));
    }
    @Test
    void disabledEmailRetainsRetryableMessageWithoutProviderCall() {
        Fixture fixture = fixture("EMAIL");
        NotificationDeliveryService disabled = new NotificationDeliveryService(db, List.of());
        NotificationDeliveryService.AttemptResult result = disabled.deliver(fixture.payload());
        assertEquals("RETRYABLE_PROVIDER", result.status());
        assertTrue(result.retryable());
        assertEquals("RETRYING", reminderStatus(fixture.reminderId()));
        assertEquals("PROVIDER_UNAVAILABLE", db.queryForObject("select error_classification from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals(0, sender.calls());
    }

    @Test
    void disabledPushDoesNotInvokeEmailSender() {
        Fixture fixture = fixture("PUSH");
        NotificationDeliveryService.AttemptResult result = notifications.deliver(fixture.payload());
        assertEquals("RETRYABLE_PROVIDER", result.status());
        assertTrue(result.retryable());
        assertEquals("PUSH", db.queryForObject("select channel from notification_attempt where correlation_id=?", String.class, result.correlationId()));
        assertEquals("RETRYING", reminderStatus(fixture.reminderId()));
        assertEquals(0, sender.calls());
    }
    @Test
    void consumerRetainsDisabledKnownChannelsForRetry() {
        Fixture email = fixture("EMAIL");
        Fixture push = fixture("PUSH");
        SqsClient sqs = mock(SqsClient.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(message(email.payload(), "email-receipt"), message(push.payload(), "push-receipt")).build());

        NotificationDeliveryService disabled = new NotificationDeliveryService(db, List.of());
        ReminderDeliveryConsumer consumer = new ReminderDeliveryConsumer(sqs, acceptance, disabled, "queue");
        assertEquals(0, consumer.pollOnce());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        assertEquals("RETRYING", reminderStatus(email.reminderId()));
        assertEquals("RETRYING", reminderStatus(push.reminderId()));
        assertEquals(0, sender.calls());
    }

    @Test
    void consumerDeletesUnsupportedChannelAfterRecordedFailure() {
        Fixture fixture = fixture("SMS");
        SqsClient sqs = mock(SqsClient.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(message(fixture.payload(), "unsupported-receipt")).build());

        NotificationDeliveryService disabled = new NotificationDeliveryService(db, List.of());
        ReminderDeliveryConsumer consumer = new ReminderDeliveryConsumer(sqs, acceptance, disabled, "queue");
        assertEquals(0, consumer.pollOnce());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        assertEquals("DELIVERY_FAILED", reminderStatus(fixture.reminderId()));
    }

    private Message message(String body, String receiptHandle) {
        return Message.builder().body(body).receiptHandle(receiptHandle).build();
    }
    private Fixture fixture(String channel) {
        var event = events.create("notification event", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "event-" + UUID.randomUUID());
        var policy = policies.create(channel, 10, "policy-" + UUID.randomUUID());
        var reminder = reminders.create(event.id(), policy.id(), "reminder-" + UUID.randomUUID());
        db.update("update schedule_outbox set available_at=? where reminder_id=?", OffsetDateTime.now().minusMinutes(1), reminder.id());
        assertEquals(1, outbox.reconcile(10));
        String payload = payload(reminder.id(), 1);
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, acceptance.acceptResult(payload));
        return new Fixture(reminder.id(), payload);
    }

    private String reminderStatus(UUID id) {
        return db.queryForObject("select status from reminders where id=?", String.class, id);
    }

    private String payload(UUID id, long version) {
        return "{\"reminderId\":\"" + id + "\",\"schedulerVersion\":" + version + ",\"idempotencyKey\":\"" + id + ":" + version + "\"}";
    }

    private record Fixture(UUID reminderId, String payload) { }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        ProbeSender notificationProbe() {
            return new ProbeSender();
        }
    }
    static class ProbeSender implements NotificationSender {
        private int calls;
        private RuntimeException nextFailure;

        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }

        @Override
        public synchronized SendResult send(SendRequest request) {
            calls++;
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            return new SendResult("provider-1");
        }

        synchronized void reset() {
            calls = 0;
            nextFailure = null;
        }

        synchronized int calls() {
            return calls;
        }

        synchronized void failNext(RuntimeException failure) {
            nextFailure = failure;
        }
    }
}
