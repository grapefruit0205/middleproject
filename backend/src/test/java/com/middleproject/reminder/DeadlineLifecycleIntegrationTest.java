package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deadline-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "app.demo-owner-id=test-owner"
})
@AutoConfigureMockMvc
class DeadlineLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SchedulerOutboxService schedulerOutbox;
    @Autowired ReminderDeliveryService delivery;

    @BeforeEach
    void reset() {
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        db.update("delete from idempotency_record");
    }

    @Test
    void updateChangesTheWholeAggregateCreatesANewScheduleGenerationAndRejectsStaleWrites() throws Exception {
        JsonNode created = create("lifecycle-create-1");
        String id = created.path("id").asText();
        String update = """
                {"title":"변경된 접수 마감","startsAt":"2036-07-13T19:30:00+09:00","leadMinutes":180,
                 "expectedVersion":0,"expectedEventVersion":0,"expectedPolicyVersion":0}
                """;

        String first = mvc.perform(put("/api/deadlines/" + id)
                        .header("Idempotency-Key", "lifecycle-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("변경된 접수 마감"))
                .andExpect(jsonPath("$.leadMinutes").value(180))
                .andExpect(jsonPath("$.status").value("SCHEDULE_PENDING"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andExpect(jsonPath("$.policyVersion").value(1))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(put("/api/deadlines/" + id)
                        .header("Idempotency-Key", "lifecycle-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(json.readTree(first), json.readTree(replay));
        assertEquals(1, count("events"));
        assertEquals(1, count("notification_policies"));
        assertEquals(1, count("reminders"));
        assertEquals(2, count("schedule_outbox"));
        assertEquals(1L, db.queryForObject(
                "select expected_version from schedule_outbox where reminder_id=? order by created_at desc, id desc limit 1",
                Long.class, java.util.UUID.fromString(id)));
        assertEquals(2L, db.queryForObject(
                "select scheduler_version from schedule_outbox where reminder_id=? order by created_at desc, id desc limit 1",
                Long.class, java.util.UUID.fromString(id)));

        mvc.perform(get("/api/deadlines/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("변경된 접수 마감"))
                .andExpect(jsonPath("$.remindAt").value("2036-07-13T07:30:00Z"));

        mvc.perform(put("/api/deadlines/" + id)
                        .header("Idempotency-Key", "stale-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isConflict());
        assertEquals(2, count("schedule_outbox"));
    }

    @Test
    void cancellationIsPersistentIdempotentAndMakesTheScheduledGenerationStale() throws Exception {
        JsonNode created = create("lifecycle-create-2");
        String id = created.path("id").asText();
        assertEquals(1, schedulerOutbox.reconcile(10));

        String oldPayload = db.queryForObject(
                "select payload from schedule_outbox where reminder_id=? and operation='UPSERT'",
                String.class, java.util.UUID.fromString(id));
        JsonNode scheduled = json.readTree(mvc.perform(get("/api/deadlines/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString());

        String cancelBody = "{\"expectedVersion\":" + scheduled.path("version").asLong() + "}";
        String first = mvc.perform(post("/api/deadlines/" + id + "/cancel")
                        .header("Idempotency-Key", "lifecycle-cancel-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(post("/api/deadlines/" + id + "/cancel")
                        .header("Idempotency-Key", "lifecycle-cancel-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(json.readTree(first), json.readTree(replay));
        assertEquals(2, count("schedule_outbox"));
        assertEquals("DELETE", db.queryForObject(
                "select operation from schedule_outbox where reminder_id=? order by created_at desc, id desc limit 1",
                String.class, java.util.UUID.fromString(id)));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(oldPayload));
        assertEquals(0, count("reminder_delivery_receipt"));
        assertEquals(0, count("notification_attempt"));

        mvc.perform(get("/api/deadlines/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private JsonNode create(String key) throws Exception {
        String response = mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"자격증 접수 마감","startsAt":"2035-06-12T18:00:00+09:00","leadMinutes":1440}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private int count(String table) {
        return db.queryForObject("select count(*) from " + table, Integer.class);
    }
}
