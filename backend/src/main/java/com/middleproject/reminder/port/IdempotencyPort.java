package com.middleproject.reminder.port;

import java.util.Optional;

public interface IdempotencyPort {
    boolean reserve(String scope, String key, String requestHash);
    Optional<IdempotencyRecord> find(String scope, String key);
    void complete(String scope, String key, int status, String body);

    record IdempotencyRecord(String requestHash, Integer responseStatus, String responseBody) {
        public boolean completed() { return responseStatus != null; }
    }
}
