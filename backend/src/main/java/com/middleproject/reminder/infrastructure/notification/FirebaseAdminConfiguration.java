package com.middleproject.reminder.infrastructure.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/** Creates the Firebase server client without writing service-account material to disk. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
public class FirebaseAdminConfiguration {

    @Bean(destroyMethod = "close")
    SecretsManagerClient firebaseSecretsManagerClient() {
        return SecretsManagerClient.create();
    }

    @Bean
    AwsFirebaseCredentialProvider firebaseCredentialProvider(SecretsManagerClient firebaseSecretsManagerClient) {
        return new AwsFirebaseCredentialProvider(firebaseSecretsManagerClient);
    }

    @Bean(destroyMethod = "delete")
    FirebaseApp tripCopilotFirebaseApp(
            AwsFirebaseCredentialProvider credentials,
            @Value("${notification.push.project-id:}") String projectId,
            @Value("${notification.push.service-account-secret-arn:}") String secretArn) throws IOException {
        if (projectId == null || projectId.isBlank() || secretArn == null || secretArn.isBlank()) {
            throw new IllegalStateException(
                    "Enabled push delivery requires notification.push.project-id and service-account-secret-arn");
        }
        byte[] json = credentials.load(secretArn);
        try (var input = new ByteArrayInputStream(json)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(input))
                    .setProjectId(projectId)
                    .build();
            return FirebaseApp.initializeApp(options, "trip-copilot");
        } finally {
            Arrays.fill(json, (byte) 0);
        }
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp tripCopilotFirebaseApp) {
        return FirebaseMessaging.getInstance(tripCopilotFirebaseApp);
    }
}
