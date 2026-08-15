package com.middleproject.reminder.application;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Contract for calling the geocoding/route providers: connect timeout of 2 seconds, response
 * timeout of 5 seconds, at most one retry with exponential backoff. Backoff is computed
 * without sleeping so tests never wait.
 */
@Component
public final class ProviderCallPolicy {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final long BACKOFF_BASE_MILLIS = 1000;

    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final int maxRetries;

    public ProviderCallPolicy() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT, DEFAULT_MAX_RETRIES);
    }

    public ProviderCallPolicy(Duration connectTimeout, Duration responseTimeout, int maxRetries) {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (responseTimeout == null || responseTimeout.isNegative() || responseTimeout.isZero()) {
            throw new IllegalArgumentException("responseTimeout must be positive");
        }
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be nonnegative");
        this.connectTimeout = connectTimeout;
        this.responseTimeout = responseTimeout;
        this.maxRetries = maxRetries;
    }

    public Duration connectTimeout() { return connectTimeout; }
    public Duration responseTimeout() { return responseTimeout; }
    public int maxRetries() { return maxRetries; }

    /** Exponential backoff in milliseconds for the given retry attempt (1-based). */
    public long backoffMillis(int attempt) {
        if (attempt <= 0) throw new IllegalArgumentException("attempt must be positive");
        return BACKOFF_BASE_MILLIS << (attempt - 1);
    }
}
