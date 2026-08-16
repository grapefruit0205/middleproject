package com.middleproject.reminder;

import com.middleproject.reminder.infrastructure.notification.FcmGateway;
import com.middleproject.reminder.infrastructure.notification.FcmPushNotificationSender;
import com.middleproject.reminder.port.DeviceRepository;
import com.middleproject.reminder.port.NotificationSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FcmPushNotificationSenderTest {

    private static final Instant NOW = Instant.parse("2026-08-20T01:00:00Z");
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final FcmGateway gateway = mock(FcmGateway.class);
    private final FcmPushNotificationSender sender = new FcmPushNotificationSender(
            devices, gateway, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsBoundedAndroidDataMessageToLatestActiveOwnerDevice() {
        UUID reminderId = UUID.randomUUID();
        when(devices.findLatestActiveFcmRegistrationToken("demo-owner", NOW))
                .thenReturn(Optional.of("registration-token"));
        Map<String, String> expectedData = Map.of(
                "reminderId", reminderId.toString(),
                "status", "DELIVERED",
                "alarmTime", Long.toString(NOW.plusSeconds(2).toEpochMilli()),
                "title", "출장 알림",
                "message", "회의 출발 시간입니다",
                "correlationId", "11111111-1111-1111-1111-111111111111");
        when(gateway.send(eq("registration-token"), eq(expectedData)))
                .thenReturn("projects/demo/messages/message-1");

        NotificationSender.SendResult result = sender.send(new NotificationSender.SendRequest(
                reminderId,
                "demo-owner",
                "출장 알림",
                "회의 출발 시간입니다",
                UUID.fromString("11111111-1111-1111-1111-111111111111")));

        assertEquals(NotificationSender.Channel.PUSH, sender.channel());
        assertEquals("projects/demo/messages/message-1", result.providerMessageId());
        verify(gateway).send("registration-token", expectedData);
    }

    @Test
    void refusesPushWhenOwnerHasNoActiveRegisteredDevice() {
        UUID reminderId = UUID.randomUUID();
        when(devices.findLatestActiveFcmRegistrationToken("demo-owner", NOW))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> sender.send(new NotificationSender.SendRequest(
                reminderId, "demo-owner", "title", "body", UUID.randomUUID())));
        verifyNoInteractions(gateway);
    }
}
