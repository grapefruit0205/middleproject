package com.middleproject.reminder.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(name = "delivery.sqs.enabled", havingValue = "true")
public class ReminderDeliveryConsumer {
    private final SqsClient sqs;
    private final ReminderDeliveryService delivery;
    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryConsumer.class);
    private final NotificationDeliveryService notifications;
    private final String queueUrl;
    private final ObservabilityMetrics metrics;

    @Autowired
    public ReminderDeliveryConsumer(SqsClient sqs, ReminderDeliveryService delivery, NotificationDeliveryService notifications,
                                    @Value("${delivery.sqs.queue-url:}") String queueUrl, ObservabilityMetrics metrics) {
        this.sqs = sqs;
        this.delivery = delivery;
        this.notifications = notifications;
        this.queueUrl = queueUrl;
        this.metrics = metrics;
    }

    public ReminderDeliveryConsumer(SqsClient sqs, ReminderDeliveryService delivery, NotificationDeliveryService notifications, String queueUrl) {
        this(sqs, delivery, notifications, queueUrl, new ObservabilityMetrics(new SimpleMeterRegistry()));
    }

    public int pollOnce() {
        int accepted = 0;
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10)
                .waitTimeSeconds(20).visibilityTimeout(60).build());
        for (var message : response.messages()) {
            var acceptance = delivery.acceptResult(message.body());
            if (acceptance == ReminderDeliveryService.AcceptResult.ACCEPTED) {
                accepted++;
                metrics.sqsAccepted();
            } else {
                metrics.sqsDuplicate();
            }
            var result = notifications.deliver(message.body());
            metrics.sqsProcessed(result.status());
            if (result.correlationId() != null) {
                MDC.put("notificationAttemptCorrelationId", result.correlationId().toString());
            }
            MDC.put("deliveryStatus", result.status());
            log.info("sqs_delivery_processed");
            MDC.remove("notificationAttemptCorrelationId");
            MDC.remove("deliveryStatus");
            if (!result.retryable()) delete(message.receiptHandle());
        }
        return accepted;
    }
    private void delete(String receiptHandle) {
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build());
    }
}
