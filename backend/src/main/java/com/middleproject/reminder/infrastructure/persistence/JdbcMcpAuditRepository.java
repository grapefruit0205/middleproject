package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.McpAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
class JdbcMcpAuditRepository implements McpAuditRepository {
    private final JdbcTemplate db;
    JdbcMcpAuditRepository(JdbcTemplate db) { this.db = db; }
    public void record(UUID id, String userId, String toolName, String requestId, String outcome, UUID reminderId, OffsetDateTime createdAt) {
        db.update("insert into mcp_audit(id,user_id,tool_name,request_id,outcome,reminder_id,created_at) values(?,?,?,?,?,?,?)", id, userId, toolName, requestId, outcome, reminderId, createdAt);
    }
}
