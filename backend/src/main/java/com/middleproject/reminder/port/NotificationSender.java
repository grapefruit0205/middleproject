package com.middleproject.reminder.port;

import java.util.UUID;

public interface NotificationSender {
    Channel channel();
    SendResult send(SendRequest request);

    enum Channel { EMAIL, PUSH }
    record SendRequest(UUID reminderId, String recipient, String subject, String body, UUID correlationId) {}
    record SendResult(String providerMessageId) {}
}
