package com.middleproject.reminder;

import com.middleproject.reminder.application.ObservabilityMetrics;
import com.middleproject.reminder.port.NotificationSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityMetricsTest {
    @Test
    void recordsSchedulerSqsAndDeliveryOutcomesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityMetrics metrics = new ObservabilityMetrics(registry);

        metrics.schedulerReconciled(true);
        metrics.schedulerReconciled(false);
        metrics.sqsAccepted();
        metrics.sqsDuplicate();
        metrics.sqsProcessed("RETRYABLE_PROVIDER");
        metrics.sqsProcessed("DELIVERY_FAILED");
        metrics.deliveryAttempt(NotificationSender.Channel.EMAIL, "SUCCEEDED");
        metrics.deliveryAttempt(NotificationSender.Channel.EMAIL, "DELIVERY_FAILED");

        assertEquals(1.0, registry.get("reminder.scheduler.reconciliations").tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get("reminder.scheduler.reconciliations").tag("outcome", "failure").counter().count());
        assertEquals(1.0, registry.get("reminder.sqs.messages").tag("outcome", "accepted").counter().count());
        assertEquals(1.0, registry.get("reminder.sqs.messages").tag("outcome", "duplicate").counter().count());
        assertEquals(1.0, registry.get("reminder.sqs.processing").tag("outcome", "retryable").counter().count());
        assertEquals(1.0, registry.get("reminder.sqs.processing").tag("outcome", "terminal").counter().count());
        assertEquals(1.0, registry.get("reminder.delivery.attempts").tag("channel", "EMAIL").tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get("reminder.delivery.attempts").tag("channel", "EMAIL").tag("outcome", "terminal").counter().count());
        assertEquals(1.0, registry.get("reminder.delivery.terminal.failures").counter().count());

        Set<String> forbiddenTagKeys = Set.of("reminderId", "recipient", "correlationId", "exception", "path", "deliveryKey");
        assertTrue(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> forbiddenTagKeys.contains(tag.getKey())));
    }
}
