package com.middleproject.reminder.transport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.transport")
public class PublicTransportProperties {

    private boolean enabled = false;

    /**
     * Security gate: Seoul real-time subway HTTP endpoint MUST be disabled by default.
     * Must not make HTTP calls unless explicitly configured to true.
     */
    private boolean seoulRealtimeEnabled = false;

    private String secretsSecretId = "reminder-platform/phase18/public-data-api-keys";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSeoulRealtimeEnabled() {
        return seoulRealtimeEnabled;
    }

    public void setSeoulRealtimeEnabled(boolean seoulRealtimeEnabled) {
        this.seoulRealtimeEnabled = seoulRealtimeEnabled;
    }

    public String getSecretsSecretId() {
        return secretsSecretId;
    }

    public void setSecretsSecretId(String secretsSecretId) {
        this.secretsSecretId = secretsSecretId;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
