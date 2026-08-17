package com.middleproject.reminder.transport.domain;

import java.util.Objects;

public sealed interface TransportOutcome<T> permits
        TransportOutcome.Success,
        TransportOutcome.Timeout,
        TransportOutcome.RateLimited,
        TransportOutcome.AuthRejected,
        TransportOutcome.Malformed,
        TransportOutcome.Empty,
        TransportOutcome.DisabledInsecure {

    enum FailureKind {
        TIMEOUT,
        RATE_LIMITED,
        AUTH_REJECTED,
        MALFORMED,
        DISABLED_INSECURE
    }

    boolean isSuccess();
    default boolean isFailure() { return !isSuccess() && !isEmpty(); }
    default boolean isEmpty() { return false; }
    default boolean isRetryable() { return false; }

    default T value() {
        throw new IllegalStateException("Outcome is not a success");
    }

    default FailureKind failureKind() {
        throw new IllegalStateException("Outcome is not a failure");
    }

    default String errorMessage() {
        return null;
    }

    static <T> TransportOutcome<T> success(T value) {
        return new Success<>(value);
    }

    static <T> TransportOutcome<T> timeout(String message) {
        return new Timeout<>(message);
    }

    static <T> TransportOutcome<T> rateLimited(String message) {
        return new RateLimited<>(message);
    }

    static <T> TransportOutcome<T> authRejected(String message) {
        return new AuthRejected<>(message);
    }

    static <T> TransportOutcome<T> malformed(String message) {
        return new Malformed<>(message);
    }

    static <T> TransportOutcome<T> empty() {
        return new Empty<>();
    }

    static <T> TransportOutcome<T> disabledInsecure(String message) {
        return new DisabledInsecure<>(message);
    }

    record Success<T>(T value) implements TransportOutcome<T> {
        public Success {
            Objects.requireNonNull(value, "value must not be null");
        }
        @Override public boolean isSuccess() { return true; }
    }

    record Timeout<T>(String errorMessage) implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public boolean isRetryable() { return true; }
        @Override public FailureKind failureKind() { return FailureKind.TIMEOUT; }
    }

    record RateLimited<T>(String errorMessage) implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public boolean isRetryable() { return true; }
        @Override public FailureKind failureKind() { return FailureKind.RATE_LIMITED; }
    }

    record AuthRejected<T>(String errorMessage) implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public FailureKind failureKind() { return FailureKind.AUTH_REJECTED; }
    }

    record Malformed<T>(String errorMessage) implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public FailureKind failureKind() { return FailureKind.MALFORMED; }
    }

    record Empty<T>() implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public boolean isEmpty() { return true; }
    }

    record DisabledInsecure<T>(String errorMessage) implements TransportOutcome<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public FailureKind failureKind() { return FailureKind.DISABLED_INSECURE; }
    }
}
