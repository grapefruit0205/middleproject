package com.middleproject.reminder;

import com.middleproject.reminder.infrastructure.notification.AwsFirebaseCredentialProvider;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsFirebaseCredentialProviderTest {

    private final SecretsManagerClient secrets = mock(SecretsManagerClient.class);
    private final AwsFirebaseCredentialProvider provider = new AwsFirebaseCredentialProvider(secrets);

    @Test
    void loadsOnlyTheRequestedSecretStringAsEphemeralBytes() {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("{\"type\":\"service_account\"}").build());

        byte[] loaded = provider.load("arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:trip-firebase");

        assertArrayEquals("{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8), loaded);
        verify(secrets).getSecretValue(GetSecretValueRequest.builder()
                .secretId("arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:trip-firebase").build());
    }

    @Test
    void rejectsMissingOrBlankSecretStrings() {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(" ").build());

        assertThrows(IllegalStateException.class, () -> provider.load(
                "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:trip-firebase"));
    }
}
