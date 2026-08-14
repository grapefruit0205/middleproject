package com.middleproject.reminder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class ReminderPlatformApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ReminderPlatformApplication.class, args);
    }

    @Bean
    Clock reminderClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "scheduler.aws.enabled", havingValue = "true")
    SchedulerClient schedulerClient() { return SchedulerClient.create(); }

    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "delivery.sqs.enabled", havingValue = "true")
    SqsClient sqsClient() { return SqsClient.create(); }

    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    SesV2Client sesClient() { return SesV2Client.create(); }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ReminderPlatformApplication.class);
    }
}
