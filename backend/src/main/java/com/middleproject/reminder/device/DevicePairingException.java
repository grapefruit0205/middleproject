package com.middleproject.reminder.device;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe failure for pairing/token problems; never leaks whether a code or token exists. */
public class DevicePairingException extends ResponseStatusException {

    public DevicePairingException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public DevicePairingException(HttpStatus status, String message) {
        super(status, message);
    }
}
