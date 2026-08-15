package com.middleproject.reminder.application;

import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.port.McpAuditRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Phase 15 single-owner noauth audit. The audit identity is always the deployment-fixed
 * Demo Owner; request Principal and X-User-Id are ignored by the MCP adapter.
 */
@Service
public class McpAuditService {
    private final McpAuditRepository repository;
    private final DemoOwnerContext demoOwner;

    public McpAuditService(McpAuditRepository repository, DemoOwnerContext demoOwner) {
        this.repository = repository;
        this.demoOwner = demoOwner;
    }

    public void record(String toolName, String requestId, String outcome, UUID reminderId) {
        repository.record(UUID.randomUUID(), demoOwner.ownerId(),
                toolName == null ? "unknown" : toolName, requestId, outcome, reminderId, OffsetDateTime.now());
    }
}
