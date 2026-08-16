package com.middleproject.reminder.infrastructure.notification;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.nio.charset.StandardCharsets;

/** Reads Firebase service-account JSON from the exact configured AWS secret into memory only. */
public class AwsFirebaseCredentialProvider {

    private final SecretsManagerClient secrets;

    public AwsFirebaseCredentialProvider(SecretsManagerClient secrets) {
        this.secrets = secrets;
    }

    public byte[] load(String secretArn) {
        String json = secrets.getSecretValue(GetSecretValueRequest.builder().secretId(secretArn).build())
                .secretString();
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Firebase service-account secret must contain a nonblank SecretString");
        }
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
