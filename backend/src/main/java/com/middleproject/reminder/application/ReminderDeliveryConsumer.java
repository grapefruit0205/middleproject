package com.middleproject.reminder.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(name = "delivery.sqs.enabled", havingValue = "true")
public class ReminderDeliveryConsumer {
    private final SqsClient sqs;
    private final ReminderDeliveryService delivery;
    private final String queueUrl;
    public ReminderDeliveryConsumer(SqsClient sqs, ReminderDeliveryService delivery, @Value("${delivery.sqs.queue-url:}") String queueUrl) { this.sqs=sqs; this.delivery=delivery; this.queueUrl=queueUrl; }

    public int pollOnce() {
        int accepted = 0;
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10)
                .waitTimeSeconds(20).visibilityTimeout(60).build());
        for (var message : response.messages()) {
            if (delivery.acceptResult(message.body()) == ReminderDeliveryService.AcceptResult.ACCEPTED) accepted++;
            sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
        }
        return accepted;
    }

}
