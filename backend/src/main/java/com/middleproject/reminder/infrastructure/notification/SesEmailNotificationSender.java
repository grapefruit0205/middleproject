package com.middleproject.reminder.infrastructure.notification;

import com.middleproject.reminder.port.NotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class SesEmailNotificationSender implements NotificationSender {
    private final SesV2Client ses;
    private final String from;

    public SesEmailNotificationSender(SesV2Client ses, @Value("${notification.email.from}") String from) {
        this.ses = ses;
        this.from = from;
    }

    @Override
    public Channel channel() { return Channel.EMAIL; }

    @Override
    public SendResult send(SendRequest request) {
        var response = ses.sendEmail(SendEmailRequest.builder().fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(request.recipient()).build())
                .content(EmailContent.builder().simple(Message.builder().subject(Content.builder().data(request.subject()).build())
                        .body(Body.builder().text(Content.builder().data(request.body()).build()).build()).build()).build()).build());
        return new SendResult(response.messageId());
    }
}
