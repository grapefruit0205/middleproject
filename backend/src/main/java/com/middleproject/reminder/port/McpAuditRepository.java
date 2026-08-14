package com.middleproject.reminder.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface McpAuditRepository {
    void record(UUID id, String userId, String toolName, String requestId, String outcome, UUID reminderId, OffsetDateTime createdAt);
}
