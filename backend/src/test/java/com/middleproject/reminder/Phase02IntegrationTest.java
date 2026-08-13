package com.middleproject.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:phase2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true"
})
class Phase02IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesReminderSchemaAndDatabaseIsQueryable() {
        assertEquals(4, jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('EVENTS','REMINDERS','NOTIFICATION_POLICIES','IDEMPOTENCY_RECORD')",
                Integer.class));
        assertTrue(jdbc.queryForObject("select count(*) from \"flyway_schema_history\"", Integer.class) >= 1);
    }

    @Test
    void databaseRejectsInvalidReminderStatus() {
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        jdbc.update("insert into events (id,title,starts_at,created_at,updated_at,version) values (?,?,?,?,?,?)",
                eventId, "event", "2030-01-01T10:00:00Z", "2030-01-01T09:00:00Z", "2030-01-01T09:00:00Z", 0);
        jdbc.update("insert into notification_policies (id,channel,lead_minutes,created_at,updated_at,version) values (?,?,?,?,?,?)",
                policyId, "EMAIL", 5, "2030-01-01T09:00:00Z", "2030-01-01T09:00:00Z", 0);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into reminders (id,event_id,policy_id,status,created_at,updated_at,version) values (?,?,?,?,?,?,?)",
                reminderId, eventId, policyId, "NOT_A_STATUS", "2030-01-01T09:00:00Z", "2030-01-01T09:00:00Z", 0));
    }
}
