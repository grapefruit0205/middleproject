package com.middleproject.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "app.demo-owner-id=postgres-owner"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_PASSWORD", matches = ".+")
class DeadlinePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("POSTGRES_TEST_PASSWORD"));
    }

    @BeforeEach
    void reset() {
        assertTrue(db.queryForObject("show server_version_num", String.class).startsWith("16"));
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        db.update("delete from idempotency_record");
    }

    @Test
    void deadlineSurvivesASeparateHttpReadAndUsesOneTransactionalAggregate() throws Exception {
        String body = """
                {"title":"PostgreSQL 마감 일정","startsAt":"2035-07-01T12:00:00+09:00","leadMinutes":60}
                """;

        mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", "postgres-deadline-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("PostgreSQL 마감 일정"));

        mvc.perform(get("/api/deadlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("PostgreSQL 마감 일정"))
                .andExpect(jsonPath("$[0].scheduleStatus").value("PENDING"));

        assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from reminders", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox", Integer.class));
        assertTrue(db.queryForObject(
                "select count(*) from flyway_schema_history where success=true and version in ('1','2','3','4','5','6')",
                Integer.class) >= 6);
    }
}
