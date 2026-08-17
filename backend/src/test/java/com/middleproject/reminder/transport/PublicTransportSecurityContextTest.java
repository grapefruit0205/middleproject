package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.infrastructure.config.PublicTransportConfiguration;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicTransportSecurityContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void transportDisabledCreatesNoSecretsManagerClientOrCredentialProviderBean() {
        contextRunner
                .withPropertyValues("app.transport.seoul-realtime-enabled=false")
                .withUserConfiguration(PublicTransportConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SecretsManagerClient.class);
                    assertThat(context).doesNotHaveBean(AwsPublicDataCredentialProvider.class);
                });
    }

    @Test
    void transportEnabledCreatesBeansWithoutFetchingSecretOnStartup() {
        SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setSecretsSecretId("reminder-platform/phase18/public-data-api-keys");

        contextRunner
                .withPropertyValues("app.transport.enabled=true")
                .withBean(PublicTransportProperties.class, () -> properties)
                .withBean(SecretsManagerClient.class, () -> mockClient)
                .withUserConfiguration(PublicTransportConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretsManagerClient.class);
                    assertThat(context).hasSingleBean(AwsPublicDataCredentialProvider.class);
                    verifyNoInteractions(mockClient);
                });
    }

    @Test
    void transportEnabledFailsWhenSecretIdIsBlank() {
        SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setSecretsSecretId("  ");

        contextRunner
                .withPropertyValues("app.transport.enabled=true")
                .withBean(PublicTransportProperties.class, () -> properties)
                .withBean(SecretsManagerClient.class, () -> mockClient)
                .withUserConfiguration(PublicTransportConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("must not be blank");
                });
    }

    @Test
    void directKeyOverridesAreRemovedFromProperties() throws Exception {
        // Assert that getSeoulOpenDataKey / getDataGoKrServiceKey methods do not exist
        assertThat(PublicTransportProperties.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("getSeoulOpenDataKey") || m.getName().equals("getDataGoKrServiceKey"));
    }
}
