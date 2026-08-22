package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Delivery attempts for one reminder in a transport-neutral map form. */
public interface DeliveryStatusRepository {
    List<Map<String, Object>> findByReminder(UUID reminderId);

    /** Typed delivery attempt for the Android companion: channel, status, attemptedAt. */
    record DeliveryAttempt(UUID reminderId, String channel, String status,
                           OffsetDateTime createdAt, OffsetDateTime completedAt) {}

    List<DeliveryAttempt> findDeliveryAttempts(UUID reminderId);
}
