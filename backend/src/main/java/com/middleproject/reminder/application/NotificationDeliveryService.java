package com.middleproject.reminder.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.NotificationSender;
import com.middleproject.reminder.port.NotificationTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

@Service
public class NotificationDeliveryService {
    private final JdbcTemplate db;
    private final List<NotificationSender> senders;
    private final ObjectMapper mapper;
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final String defaultRecipient;
    private final ObservabilityMetrics metrics;

    @Autowired
    public NotificationDeliveryService(JdbcTemplate db, List<NotificationSender> senders, ObjectMapper mapper,
                                       @Value("${notification.email.to:}") String defaultRecipient,
                                       ObservabilityMetrics metrics) {
        this.db = db;
        this.senders = senders;
        this.mapper = mapper;
        this.defaultRecipient = defaultRecipient;
        this.metrics = metrics;
    }

    public NotificationDeliveryService(JdbcTemplate db, NotificationSender sender) {
        this(db, List.of(sender), new ObjectMapper(), "", new ObservabilityMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    public NotificationDeliveryService(JdbcTemplate db, List<NotificationSender> senders) {
        this(db, senders, new ObjectMapper(), "", new ObservabilityMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Transactional
    public AttemptResult deliver(UUID reminderId, String recipient, String subject, String body) {
        Context context;
        try {
            context = db.queryForObject(
                    "select r.status, p.channel from reminders r join notification_policies p on p.id=r.policy_id where r.id=? for update",
                    (rs, n) -> new Context(rs.getString("status"), null, rs.getString("channel")), reminderId);
        } catch (EmptyResultDataAccessException e) {
            return new AttemptResult(null, "STALE", null);
        }
        ReminderStatus currentStatus = parseStatus(context.status());
        if (currentStatus == ReminderStatus.DELIVERED || currentStatus == ReminderStatus.ACKNOWLEDGED) {
            return new AttemptResult(null, "ALREADY_DELIVERED", null);
        }
        if (!isDeliverable(currentStatus)) {
            return new AttemptResult(null, "ALREADY_PROCESSED", null);
        }
        return deliver(reminderId, NotificationSender.Channel.EMAIL, recipient, subject, body, currentStatus,
                "direct:" + UUID.randomUUID());
    }
    @Transactional
    public AttemptResult deliver(String schedulerPayload) {
        Delivery payload;
        try {
            payload = mapper.readValue(schedulerPayload, Delivery.class);
        } catch (Exception e) {
            return new AttemptResult(null, "MALFORMED", null);
        }
        if (payload.reminderId == null || payload.schedulerVersion == null || payload.schedulerVersion < 0
                || payload.idempotencyKey == null
                || !payload.idempotencyKey.equals(payload.reminderId + ":" + payload.schedulerVersion)) {
            return new AttemptResult(null, "MALFORMED", null);
        }
        Context context;
        try {
            context = db.queryForObject(
                    "select r.status, e.title, p.channel from reminder_delivery_receipt d "
                            + "join reminders r on r.id=d.reminder_id join events e on e.id=r.event_id "
                            + "join notification_policies p on p.id=r.policy_id "
                            + "where d.idempotency_key=? and d.reminder_id=? and d.scheduler_version=? for update",
                    (rs, n) -> new Context(rs.getString("status"), rs.getString("title"), rs.getString("channel")),
                    payload.idempotencyKey, payload.reminderId, payload.schedulerVersion);
        } catch (EmptyResultDataAccessException e) {
            return new AttemptResult(null, "STALE", null);
        }

        ReminderStatus currentStatus = parseStatus(context.status());
        if (currentStatus == ReminderStatus.DELIVERED || currentStatus == ReminderStatus.ACKNOWLEDGED) {
            return new AttemptResult(null, "ALREADY_DELIVERED", null);
        }
        if (!isDeliverable(currentStatus)) {
            return new AttemptResult(null, "ALREADY_PROCESSED", null);
        }
        String rawChannel = context.channel();
        NotificationSender.Channel channel;
        try {
            channel = NotificationSender.Channel.valueOf(rawChannel.toUpperCase());
        } catch (RuntimeException e) {
            return deliver(payload.reminderId, rawChannel, defaultRecipient,
                    "Reminder: " + context.title(), context.title(), currentStatus, payload.idempotencyKey);
        }
        return deliver(payload.reminderId, channel, defaultRecipient,
                "Reminder: " + context.title(), context.title(), currentStatus, payload.idempotencyKey);
    }
    private AttemptResult deliver(UUID reminderId, NotificationSender.Channel channel, String recipient, String subject,
                                  String body, ReminderStatus currentStatus, String deliveryKey) {
        return deliver(reminderId, channel.name(), recipient, subject, body, currentStatus, deliveryKey);
    }

    private AttemptResult deliver(UUID reminderId, String rawChannel, String recipient, String subject, String body,
                                  ReminderStatus currentStatus, String deliveryKey) {
        UUID correlationId = UUID.randomUUID();
        insertAttempt(reminderId, deliveryKey, rawChannel, recipient, correlationId);
        NotificationSender.Channel channel;
        try {
            channel = NotificationSender.Channel.valueOf(rawChannel.toUpperCase());
        } catch (RuntimeException e) {
            channel = null;
        }
        NotificationSender.Channel resolvedChannel = channel;
        NotificationSender sender = resolvedChannel == null
                ? null
                : senders.stream().filter(candidate -> candidate.channel() == resolvedChannel).findFirst().orElse(null);
        String status = "SUCCEEDED";
        String provider = null;
        String errorClass = null;
        String error = null;
        if (sender == null) {
            if (channel == null) {
                status = "DELIVERY_FAILED";
                errorClass = "UNSUPPORTED_CHANNEL";
            } else {
                status = "RETRYABLE_PROVIDER";
                errorClass = "PROVIDER_UNAVAILABLE";
            }
            error = "No sender configured for " + rawChannel;
        } else {
            try {
                NotificationSender.SendResult result = sender.send(
                        new NotificationSender.SendRequest(reminderId, recipient, subject, body, correlationId));
                provider = result.providerMessageId();
            } catch (RuntimeException failure) {
                status = classify(failure);
                errorClass = status;
                error = failure.getMessage();
            }
        }
        complete(correlationId, status, provider, errorClass, error);
        metrics.deliveryAttempt(rawChannel, status);
        MDC.put("reminderId", reminderId.toString());
        MDC.put("deliveryKey", deliveryKey);
        MDC.put("notificationAttemptCorrelationId", correlationId.toString());
        MDC.put("deliveryStatus", status);
        log.info("notification_delivery_completed");
        MDC.remove("reminderId");
        MDC.remove("deliveryKey");
        MDC.remove("notificationAttemptCorrelationId");
        MDC.remove("deliveryStatus");
        ReminderStatus target = "SUCCEEDED".equals(status)
                ? ReminderStatus.DELIVERED
                : ("RETRYABLE_TIMEOUT".equals(status) || "RETRYABLE_PROVIDER".equals(status)
                ? ReminderStatus.RETRYING : ReminderStatus.DELIVERY_FAILED);
        transitionReminder(reminderId, currentStatus, target);
        return new AttemptResult(correlationId, status, provider);
    }

    private void insertAttempt(UUID reminderId, String deliveryKey, String channel, String recipient, UUID correlationId) {
        db.update("insert into notification_attempt "
                        + "(id,reminder_id,correlation_id,delivery_key,active_delivery_key,channel,recipient,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), reminderId, correlationId, deliveryKey, deliveryKey, channel, recipient, "STARTED", OffsetDateTime.now());
    }

    private void complete(UUID id, String status, String provider, String errorClass, String error) {
        db.update("update notification_attempt set status=?,provider_message_id=?,error_classification=?,error_message=?,completed_at=?,active_delivery_key=null where correlation_id=?",
                status, provider, errorClass, error, OffsetDateTime.now(), id);
    }

    private void transitionReminder(UUID reminderId, ReminderStatus currentStatus, ReminderStatus target) {
        if (currentStatus == null || !currentStatus.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid reminder transition: " + currentStatus + " -> " + target);
        }
        int updated = db.update("update reminders set status=?,updated_at=?,version=version+1 where id=? and status=?",
                target.name(), OffsetDateTime.now(), reminderId, currentStatus.name());
        if (updated != 1) {
            throw new IllegalStateException("Reminder delivery claim was lost for " + reminderId);
        }
    }

    private ReminderStatus parseStatus(String rawStatus) {
        try {
            return ReminderStatus.valueOf(rawStatus);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isDeliverable(ReminderStatus status) {
        return status == ReminderStatus.DISPATCHED || status == ReminderStatus.RETRYING;
    }
    static String classify(Throwable failure) {
        if (failure instanceof NotificationTimeoutException) return "RETRYABLE_TIMEOUT";
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException) return "RETRYABLE_TIMEOUT";
        }
        if (failure instanceof DataAccessException) return "PERSISTENCE_FAILURE";
        if (failure instanceof SdkClientException) return "RETRYABLE_PROVIDER";
        if (failure instanceof AwsServiceException service) {
            String code = service.awsErrorDetails() == null ? "" : service.awsErrorDetails().errorCode();
            if (service.statusCode() >= 500 || service.isThrottlingException()
                    || Set.of("Throttling", "TooManyRequestsException", "ServiceUnavailable", "InternalFailure", "RequestTimeout").contains(code)) {
                return "RETRYABLE_PROVIDER";
            }
        }
        return "PROVIDER_FAILURE";
    }

    public record AttemptResult(UUID correlationId, String status, String providerMessageId) {
        public boolean retryable() {
            return "RETRYING".equals(status) || "RETRYABLE_TIMEOUT".equals(status) || "RETRYABLE_PROVIDER".equals(status);
        }
    }

    private record Context(String status, String title, String channel) { }

    private static class Delivery {
        public UUID reminderId;
        public Long schedulerVersion;
        public String idempotencyKey;
    }
}
