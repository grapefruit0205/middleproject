package com.middleproject.reminder.port;

/** Provider-specific failure with an explicit retry decision. */
public class NotificationProviderException extends RuntimeException {
    private final boolean retryable;

    public NotificationProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
