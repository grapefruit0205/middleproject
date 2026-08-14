package com.middleproject.reminder.application;

import com.middleproject.reminder.port.McpAuditRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class McpAuditService {
    private final McpAuditRepository repository;

    public McpAuditService(McpAuditRepository repository) { this.repository = repository; }

    public void record(String userId, String toolName, String requestId, String outcome, UUID reminderId) {
        repository.record(UUID.randomUUID(), userId == null ? "unknown" : userId,
                toolName == null ? "unknown" : toolName, requestId, outcome, reminderId, OffsetDateTime.now());
    }
}
