package com.middleproject.reminder;

import com.middleproject.reminder.infrastructure.notification.SesEmailNotificationSender;
import com.middleproject.reminder.port.NotificationSender;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SesEmailNotificationSenderTest {
    @Test
    void sendsExpectedSesRequestAndReturnsProviderMessageId() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().messageId("ses-message-42").build());
        SesEmailNotificationSender sender = new SesEmailNotificationSender(ses, "from@example.test");
        UUID reminderId = UUID.randomUUID();

        NotificationSender.SendResult result = sender.send(new NotificationSender.SendRequest(
                reminderId, "to@example.test", "Reminder subject", "Reminder body", UUID.randomUUID()));

        assertEquals("ses-message-42", result.providerMessageId());
        var request = org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        assertEquals("from@example.test", request.getValue().fromEmailAddress());
        assertEquals(1, request.getValue().destination().toAddresses().size());
        assertEquals("to@example.test", request.getValue().destination().toAddresses().get(0));
        assertEquals("Reminder subject", request.getValue().content().simple().subject().data());
        assertEquals("Reminder body", request.getValue().content().simple().body().text().data());
    }
}
