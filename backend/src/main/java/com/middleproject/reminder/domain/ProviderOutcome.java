package com.middleproject.reminder.domain;

import java.util.Objects;

/**
 * Typed outcome of an external provider call. A provider failure must never surface as an
 * exception from an adapter; it is returned as one of these outcomes so the application can
 * decide whether a retry is meaningful.
 */
public sealed interface ProviderOutcome<T> permits ProviderOutcome.Success, ProviderOutcome.Timeout,
        ProviderOutcome.RateLimited, ProviderOutcome.Empty, ProviderOutcome.Malformed {

    enum Kind { SUCCESS, TIMEOUT, RATE_LIMITED, EMPTY, MALFORMED }

    Kind kind();

    default boolean success() { return false; }
    default boolean retryable() { return false; }
    default T value() { throw new IllegalStateException("No value for outcome " + kind()); }

    record Success<T>(T value) implements ProviderOutcome<T> {
        public Success { value = Objects.requireNonNull(value, "value"); }
        @Override public Kind kind() { return Kind.SUCCESS; }
        @Override public boolean success() { return true; }
        @Override public T value() { return value; }
    }

    record Timeout<T>() implements ProviderOutcome<T> {
        @Override public Kind kind() { return Kind.TIMEOUT; }
        @Override public boolean retryable() { return true; }
    }

    record RateLimited<T>() implements ProviderOutcome<T> {
        @Override public Kind kind() { return Kind.RATE_LIMITED; }
        @Override public boolean retryable() { return true; }
    }

    record Empty<T>() implements ProviderOutcome<T> {
        @Override public Kind kind() { return Kind.EMPTY; }
    }

    record Malformed<T>(String reason) implements ProviderOutcome<T> {
        public Malformed { reason = Objects.requireNonNull(reason, "reason"); }
        @Override public Kind kind() { return Kind.MALFORMED; }
    }
}
