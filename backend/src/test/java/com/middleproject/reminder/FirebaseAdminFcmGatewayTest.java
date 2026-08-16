package com.middleproject.reminder;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.middleproject.reminder.infrastructure.notification.FirebaseAdminFcmGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseAdminFcmGatewayTest {

    @Test
    void delegatesOneDataOnlyMessageAndReturnsProviderMessageId() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class))).thenReturn("projects/demo/messages/42");
        FirebaseAdminFcmGateway gateway = new FirebaseAdminFcmGateway(messaging);

        String result = gateway.send("registered-device", Map.of("reminderId", "r1", "status", "DELIVERED"));

        assertEquals("projects/demo/messages/42", result);
        verify(messaging).send(any(Message.class));
    }
}
