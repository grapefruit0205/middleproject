package com.middleproject.reminder;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLoggingContractTest {
    @Test
    void awsProfileUsesEcsJsonFileLogsWithBoundedRetentionAndNoSecretCanaries() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String aws = Files.readString(Path.of("src/main/resources/application-aws.yml"));

        assertTrue(application.contains("structured:"));
        assertTrue(application.contains("format:"));
        assertTrue(aws.contains("ecs"));
        assertTrue(aws.contains("/var/log/middleproject/application.json"));
        assertTrue(aws.contains("max-file-size"));
        assertTrue(aws.contains("max-history"));
        assertTrue(aws.contains("INSTANCE_ID"));
        assertTrue(aws.contains("ENVIRONMENT"));

        for (String canary : new String[]{"authorization-canary", "database-password-canary", "secret-payload-canary", "notification-body-canary", "recipient-canary@example.test"}) {
            assertFalse(application.contains(canary));
            assertFalse(aws.contains(canary));
        }
    }
}
