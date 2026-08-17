package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsPublicDataCredentialProviderTest {

    private final SecretsManagerClient secrets = mock(SecretsManagerClient.class);
    private final AwsPublicDataCredentialProvider provider = new AwsPublicDataCredentialProvider(secrets);

    @Test
    void loadsExactJsonKeysIntoMemoryWithoutPersisting() {
        String json = "{\"seoulOpenDataKey\":\"sample-seoul-key-123\",\"dataGoKrServiceKey\":\"sample-datagokr-key-456\"}";
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(json).build());

        PublicDataCredentials creds = provider.load("reminder-platform/phase18/public-data-api-keys");

        assertNotNull(creds);
        assertEquals("sample-seoul-key-123", creds.seoulOpenDataKey());
        assertEquals("sample-datagokr-key-456", creds.dataGoKrServiceKey());

        // toString must NEVER leak keys
        assertFalse(creds.toString().contains("sample-seoul-key-123"));
        assertFalse(creds.toString().contains("sample-datagokr-key-456"));
    }

    @Test
    void rejectsMissingOrBlankRequiredFields() {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("{\"seoulOpenDataKey\":\"\"}").build());

        assertThrows(IllegalStateException.class, () -> provider.load("reminder-platform/phase18/public-data-api-keys"));
    }

    @Test
    void rejectsMalformedJson() {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("{not-valid-json").build());

        assertThrows(IllegalStateException.class, () -> provider.load("reminder-platform/phase18/public-data-api-keys"));
    }
}
