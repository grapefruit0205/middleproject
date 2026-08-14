package com.middleproject.reminder;

import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.application.ReminderDeliveryConsumer;
import com.middleproject.reminder.application.ReminderDeliveryService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReminderDeliveryConsumerTest {
    private static final String BODY = "payload";

    @Test void ignoredAcceptanceStillDeliversAndCountsOnlyAccepted() {
        SqsClient sqs = mock(SqsClient.class);
        ReminderDeliveryService delivery = mock(ReminderDeliveryService.class);
        NotificationDeliveryService notifications = mock(NotificationDeliveryService.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(message()).build());
        when(delivery.acceptResult(BODY)).thenReturn(ReminderDeliveryService.AcceptResult.IGNORED);
        when(notifications.deliver(BODY)).thenReturn(new NotificationDeliveryService.AttemptResult(null, "RETRYABLE_PROVIDER", null));

        assertEquals(0, new ReminderDeliveryConsumer(sqs, delivery, notifications, "queue").pollOnce());
        verify(notifications).deliver(BODY);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test void retryableResultIsNotDeleted() {
        SqsClient sqs = mock(SqsClient.class);
        ReminderDeliveryService delivery = mock(ReminderDeliveryService.class);
        NotificationDeliveryService notifications = mock(NotificationDeliveryService.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(message()).build());
        when(delivery.acceptResult(BODY)).thenReturn(ReminderDeliveryService.AcceptResult.ACCEPTED);
        when(notifications.deliver(BODY)).thenReturn(new NotificationDeliveryService.AttemptResult(null, "RETRYABLE_TIMEOUT", null));

        assertEquals(1, new ReminderDeliveryConsumer(sqs, delivery, notifications, "queue").pollOnce());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test void malformedResultIsDeleted() {
        SqsClient sqs = mock(SqsClient.class);
        ReminderDeliveryService delivery = mock(ReminderDeliveryService.class);
        NotificationDeliveryService notifications = mock(NotificationDeliveryService.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(message()).build());
        when(delivery.acceptResult(BODY)).thenReturn(ReminderDeliveryService.AcceptResult.IGNORED);
        when(notifications.deliver(BODY)).thenReturn(new NotificationDeliveryService.AttemptResult(null, "MALFORMED", null));

        assertEquals(0, new ReminderDeliveryConsumer(sqs, delivery, notifications, "queue").pollOnce());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    private static Message message() {
        return Message.builder().body(BODY).receiptHandle("receipt").build();
    }
}
