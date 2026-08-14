package com.middleproject.reminder.application;

import com.middleproject.reminder.port.NotificationSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ObservabilityMetrics {
    private final MeterRegistry registry;

    public ObservabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void schedulerReconciled(boolean success) {
        record(() -> Counter.builder("reminder.scheduler.reconciliations")
                .tag("operation", "reconcile")
                .tag("outcome", success ? "success" : "failure")
                .register(registry).increment());
    }

    public void sqsAccepted() {
        record(() -> Counter.builder("reminder.sqs.messages")
                .tag("operation", "accept")
                .tag("outcome", "accepted")
                .register(registry).increment());
    }

    public void sqsDuplicate() {
        record(() -> Counter.builder("reminder.sqs.messages")
                .tag("operation", "accept")
                .tag("outcome", "duplicate")
                .register(registry).increment());
    }

    public void sqsProcessed(String status) {
        String outcome = "SUCCEEDED".equals(status) ? "success" : (status.startsWith("RETRYABLE") ? "retryable" : "terminal");
        record(() -> Counter.builder("reminder.sqs.processing")
                .tag("operation", "deliver")
                .tag("outcome", outcome)
                .register(registry).increment());
    }

    public void deliveryAttempt(NotificationSender.Channel channel, String status) {
        deliveryAttempt(channel.name(), status);
    }

    public void deliveryAttempt(String rawChannel, String status) {
        String channel = "EMAIL".equalsIgnoreCase(rawChannel) || "PUSH".equalsIgnoreCase(rawChannel)
                ? rawChannel.toUpperCase() : "UNKNOWN";
        String outcome = "SUCCEEDED".equals(status) ? "success" : (status.startsWith("RETRYABLE") ? "retryable" : "terminal");
        record(() -> Counter.builder("reminder.delivery.attempts")
                .tag("channel", channel)
                .tag("outcome", outcome)
                .register(registry).increment());
        if ("terminal".equals(outcome)) {
            record(() -> Counter.builder("reminder.delivery.terminal.failures").register(registry).increment());
        }
    }

    private void record(Runnable measurement) {
        try {
            measurement.run();
        } catch (RuntimeException ignored) {
        }
    }
}
