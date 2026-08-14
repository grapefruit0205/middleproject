package com.middleproject.reminder.port;

/** Runtime boundary exception preserving timeout causes without checked exceptions on the port. */
public class NotificationTimeoutException extends RuntimeException {
    public NotificationTimeoutException(String message, Throwable cause) { super(message, cause); }
}
