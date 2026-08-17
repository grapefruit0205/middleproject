package com.middleproject.reminder.transport.infrastructure.config;

import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.transport.enabled", havingValue = "true")
public class PublicTransportConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    SecretsManagerClient publicTransportSecretsManagerClient() {
        return SecretsManagerClient.create();
    }

    @Bean
    @ConditionalOnMissingBean
    AwsPublicDataCredentialProvider awsPublicDataCredentialProvider(
            SecretsManagerClient publicTransportSecretsManagerClient,
            PublicTransportProperties properties) {
        String secretId = properties != null ? properties.getSecretsSecretId() : null;
        if (secretId == null || secretId.isBlank()) {
            throw new IllegalStateException("app.transport.secrets-secret-id must not be blank when app.transport.enabled is true");
        }
        return new AwsPublicDataCredentialProvider(publicTransportSecretsManagerClient);
    }
}
