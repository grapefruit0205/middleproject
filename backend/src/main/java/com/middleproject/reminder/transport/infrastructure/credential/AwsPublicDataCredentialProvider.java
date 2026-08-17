package com.middleproject.reminder.transport.infrastructure.credential;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.Objects;

/**
 * Reads public data API keys from AWS Secrets Manager JSON into memory only.
 * Secret keys expected: seoulOpenDataKey, dataGoKrServiceKey. kakaoLocalRestApiKey is optional.
 */
public class AwsPublicDataCredentialProvider {

    public static final String DEFAULT_SECRET_ID = "reminder-platform/phase18/public-data-api-keys";

    private final SecretsManagerClient secretsClient;
    private final ObjectMapper mapper;

    public AwsPublicDataCredentialProvider(SecretsManagerClient secretsClient) {
        this(secretsClient, new ObjectMapper());
    }

    public AwsPublicDataCredentialProvider(SecretsManagerClient secretsClient, ObjectMapper mapper) {
        this.secretsClient = Objects.requireNonNull(secretsClient, "secretsClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public PublicDataCredentials load(String secretId) {
        if (secretId == null || secretId.isBlank()) {
            throw new IllegalArgumentException("secretId must not be null or blank");
        }
        try {
            String secretJson = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build()
            ).secretString();

            if (secretJson == null || secretJson.isBlank()) {
                throw new IllegalStateException("Public data API keys secret is empty or missing SecretString");
            }

            JsonNode root = mapper.readTree(secretJson);
            if (!root.isObject()) {
                throw new IllegalStateException("Secret JSON must be a JSON object");
            }

            JsonNode seoulNode = root.get("seoulOpenDataKey");
            JsonNode dataGoKrNode = root.get("dataGoKrServiceKey");
            JsonNode kakaoNode = root.get("kakaoLocalRestApiKey");

            if (seoulNode == null || !seoulNode.isTextual() || seoulNode.textValue().isBlank()) {
                throw new IllegalStateException("Missing or blank seoulOpenDataKey in secret JSON");
            }
            if (dataGoKrNode == null || !dataGoKrNode.isTextual() || dataGoKrNode.textValue().isBlank()) {
                throw new IllegalStateException("Missing or blank dataGoKrServiceKey in secret JSON");
            }

            String kakaoKey = kakaoNode != null && kakaoNode.isTextual() && !kakaoNode.textValue().isBlank()
                    ? kakaoNode.textValue().trim()
                    : null;
            return new PublicDataCredentials(seoulNode.textValue().trim(), dataGoKrNode.textValue().trim(), kakaoKey);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load or parse public data credentials from Secrets Manager", e);
        }
    }
}
