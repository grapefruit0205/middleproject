package com.middleproject.reminder.infrastructure.notification;

import com.middleproject.reminder.port.NotificationSender;

/** Explicit boundary for a future push provider; it is never selected by default. */
public class DisabledPushNotificationSender implements NotificationSender {
    @Override
    public Channel channel() { return Channel.PUSH; }

    @Override
    public SendResult send(SendRequest request) {
        throw new UnsupportedOperationException("Push notifications are disabled");
    }
}
