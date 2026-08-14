package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface IdempotencyPort {
    String reserve(String scope, String key, String requestHash);
    String claimExpired(String scope, String key, String requestHash);
    String claimFailed(String scope, String key, String requestHash);
    Optional<IdempotencyRecord> find(String scope, String key);
    boolean complete(String scope, String key, String token, int status, String body);
    boolean fail(String scope, String key, String token, String error);

    record IdempotencyRecord(String requestHash, String status, String claimToken, Integer responseStatus, String responseBody,
                             Integer attempts, String lastError, OffsetDateTime leaseUntil, OffsetDateTime lastClaimAt) {
        public boolean completed() { return "COMPLETED".equals(status); }
        public boolean failed() { return "FAILED".equals(status); }
        public boolean leaseExpired(OffsetDateTime now) { return leaseUntil == null || !leaseUntil.isAfter(now); }
    }
}
