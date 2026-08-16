package com.middleproject.reminder.infrastructure.notification;

import com.middleproject.reminder.port.DeviceRepository;
import com.middleproject.reminder.port.NotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends one bounded data-only reminder to the demo owner's latest active Android device. */
@Component
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
public class FcmPushNotificationSender implements NotificationSender {

    private static final int MAX_DATA_VALUE_LENGTH = 300;
    private final DeviceRepository devices;
    private final FcmGateway gateway;
    private final Clock clock;

    public FcmPushNotificationSender(DeviceRepository devices, FcmGateway gateway, Clock clock) {
        this.devices = devices;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    public Channel channel() {
        return Channel.PUSH;
    }

    @Override
    public SendResult send(SendRequest request) {
        Instant now = clock.instant();
        String token = devices.findLatestActiveFcmRegistrationToken(request.recipient(), now)
                .orElseThrow(() -> new IllegalStateException("No active Android push registration for owner"));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("reminderId", request.reminderId().toString());
        data.put("status", "DELIVERED");
        data.put("alarmTime", Long.toString(now.plusSeconds(2).toEpochMilli()));
        data.put("title", bounded(request.subject(), "Reminder"));
        data.put("message", bounded(request.body(), "You have a reminder"));
        data.put("correlationId", request.correlationId().toString());

        return new SendResult(gateway.send(token, Map.copyOf(data)));
    }

    private String bounded(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() <= MAX_DATA_VALUE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_DATA_VALUE_LENGTH);
    }
}
