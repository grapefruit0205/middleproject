package com.middleproject.reminder.infrastructure.notification;

import java.util.Map;

/** Narrow Firebase boundary so message construction is deterministic in unit tests. */
public interface FcmGateway {
    String send(String registrationToken, Map<String, String> data);
}
