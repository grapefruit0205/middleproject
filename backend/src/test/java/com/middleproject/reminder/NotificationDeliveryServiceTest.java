package com.middleproject.reminder;

import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.port.NotificationSender;
import com.middleproject.reminder.port.NotificationTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;

import java.util.UUID;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final NotificationDeliveryService service = new NotificationDeliveryService(db, sender);
    private final UUID reminderId = UUID.randomUUID();

    @BeforeEach
    void configureSenderChannel() throws Exception {
        when(sender.channel()).thenReturn(NotificationSender.Channel.EMAIL);
        when(db.queryForObject(startsWith("select r.status, p.channel"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("status")).thenReturn("DISPATCHED");
                    when(rs.getString("channel")).thenReturn("EMAIL");
                    return mapper.mapRow(rs, 1);
                });
        when(db.update(startsWith("update reminders set status=?"), any(Object[].class))).thenReturn(1);
    }

    @Test void emailSuccessPersistsProviderResponse() {
        when(sender.send(any())).thenReturn(new NotificationSender.SendResult("ses-123"));
        var result = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertEquals("SUCCEEDED", result.status());
        assertEquals("ses-123", result.providerMessageId());
        verify(db).update(startsWith("insert into notification_attempt"), any(Object[].class));
        verify(db).update(startsWith("update notification_attempt"), eq("SUCCEEDED"), eq("ses-123"), isNull(), isNull(), any(), eq(result.correlationId()));
        verify(db).update(startsWith("update reminders set status=?"), eq("DELIVERED"), any(), eq(reminderId), eq("DISPATCHED"));
        verify(db, never()).update(contains("ACKNOWLEDGED"), any(Object[].class));
    }

    @Test void providerFailureIsPersisted() {
        when(sender.send(any())).thenThrow(new IllegalStateException("rejected"));
        var result = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertEquals("PROVIDER_FAILURE", result.status());
        verify(db).update(startsWith("update notification_attempt"), eq("PROVIDER_FAILURE"), isNull(), eq("PROVIDER_FAILURE"), eq("rejected"), any(), eq(result.correlationId()));
    }

    @Test void timeoutWithoutCauseIsRetryable() {
        when(sender.send(any())).thenThrow(new NotificationTimeoutException("slow SES", null));
        var result = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertEquals("RETRYABLE_TIMEOUT", result.status());
        assertTrue(result.retryable());
    }

    @Test void timeoutIsRetryableAndPersisted() {
        when(sender.send(any())).thenThrow(new NotificationTimeoutException("slow SES", new TimeoutException("slow SES")));
        var result = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertEquals("RETRYABLE_TIMEOUT", result.status());
        verify(db).update(startsWith("update notification_attempt"), eq("RETRYABLE_TIMEOUT"), isNull(), eq("RETRYABLE_TIMEOUT"), eq("slow SES"), any(), eq(result.correlationId()));
    }

    @Test void sdkClientFailureIsRetryableProvider() {
        when(sender.send(any())).thenThrow(SdkClientException.create("network"));
        var result = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertEquals("RETRYABLE_PROVIDER", result.status());
        assertTrue(result.retryable());
    }

    @Test void eachAttemptGetsItsOwnCorrelationId() {
        when(sender.send(any())).thenReturn(new NotificationSender.SendResult("one"), new NotificationSender.SendResult("two"));
        var first = service.deliver(reminderId, "a@example.test", "subject", "body");
        var second = service.deliver(reminderId, "a@example.test", "subject", "body");
        assertNotEquals(first.correlationId(), second.correlationId());
    }

    @Test void unsupportedChannelCreatesFailedAttemptAndIsNonRetryable() {
        var payload = "{\"reminderId\":\"" + reminderId + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminderId + ":1\"}";
        when(db.queryForObject(anyString(), any(RowMapper.class), any(), any(), any())).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("status")).thenReturn("DISPATCHED");
            when(rs.getString("title")).thenReturn("title");
            when(rs.getString("channel")).thenReturn("SMS");
            return mapper.mapRow(rs, 1);
        });
        when(db.update(startsWith("update reminders set status=?"), any(), any(), any(), any())).thenReturn(1);
        var result = service.deliver(payload);
        assertEquals("DELIVERY_FAILED", result.status());
        assertFalse(result.retryable());
        verify(db).update(startsWith("update notification_attempt"), eq("DELIVERY_FAILED"), isNull(), eq("UNSUPPORTED_CHANNEL"), contains("SMS"), any(), eq(result.correlationId()));
        verify(db).update(startsWith("update reminders set status=?"), eq("DELIVERY_FAILED"), any(), eq(reminderId), eq("DISPATCHED"));
    }

    @Test void disabledPushBoundaryDoesNotChangeEmailSender() {
        assertThrows(UnsupportedOperationException.class, () -> new com.middleproject.reminder.infrastructure.notification.DisabledPushNotificationSender()
                .send(new NotificationSender.SendRequest(reminderId, "a@example.test", "s", "b", UUID.randomUUID())));
        verifyNoInteractions(sender);
    }

    @Test void scheduledPushRoutesByServerResolvedTripOwnerInsteadOfEmailRecipient() throws Exception {
        NotificationSender push = mock(NotificationSender.class);
        when(push.channel()).thenReturn(NotificationSender.Channel.PUSH);
        when(push.send(any())).thenReturn(new NotificationSender.SendResult("fcm-message"));
        NotificationDeliveryService pushService = new NotificationDeliveryService(db, java.util.List.of(push));
        String payload = "{\"reminderId\":\"" + reminderId
                + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminderId + ":1\"}";
        when(db.queryForObject(contains("join notification_policies"), any(RowMapper.class), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("status")).thenReturn("DISPATCHED");
                    when(rs.getString("title")).thenReturn("출장 회의");
                    when(rs.getString("channel")).thenReturn("PUSH");
                    when(rs.getString("owner_id")).thenReturn("demo-owner");
                    return mapper.mapRow(rs, 1);
                });
        when(db.update(startsWith("update reminders set status=?"), any(), any(), any(), any())).thenReturn(1);

        var result = pushService.deliver(payload);

        assertEquals("SUCCEEDED", result.status());
        verify(push).send(argThat(request -> "demo-owner".equals(request.recipient())));
    }
}
