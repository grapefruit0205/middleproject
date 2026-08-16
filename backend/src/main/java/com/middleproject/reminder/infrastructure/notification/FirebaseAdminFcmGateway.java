package com.middleproject.reminder.infrastructure.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.middleproject.reminder.port.NotificationProviderException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Firebase Admin SDK adapter. Registration tokens are accepted during Firebase's FID migration window. */
@Component
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
public class FirebaseAdminFcmGateway implements FcmGateway {

    private static final Set<MessagingErrorCode> RETRYABLE = Set.of(
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.UNAVAILABLE,
            MessagingErrorCode.QUOTA_EXCEEDED);

    private final FirebaseMessaging messaging;

    public FirebaseAdminFcmGateway(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String send(String registrationToken, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(registrationToken)
                .putAllData(data)
                .build();
        try {
            return messaging.send(message);
        } catch (FirebaseMessagingException failure) {
            MessagingErrorCode code = failure.getMessagingErrorCode();
            throw new NotificationProviderException(
                    "FCM rejected the reminder message" + (code == null ? "" : " (" + code + ")"),
                    code != null && RETRYABLE.contains(code),
                    failure);
        }
    }
}
