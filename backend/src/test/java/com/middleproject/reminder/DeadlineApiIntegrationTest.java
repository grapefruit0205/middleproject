package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deadline-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "app.demo-owner-id=test-owner"
})
@AutoConfigureMockMvc
class DeadlineApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

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
    void createsAggregateAtomicallyListsItAndReplaysTheSameRequest() throws Exception {
        String body = """
                {"title":"자격증 접수 마감","startsAt":"2035-06-12T18:00:00+09:00","leadMinutes":1440}
                """;

        String first = mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", "deadline-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("자격증 접수 마감"))
                .andExpect(jsonPath("$.leadMinutes").value(1440))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.scheduleStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", "deadline-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(json.readTree(first), json.readTree(replay));
        assertEquals(1, count("events"));
        assertEquals(1, count("notification_policies"));
        assertEquals(1, count("reminders"));
        assertEquals(1, count("schedule_outbox"));

        JsonNode created = json.readTree(first);
        mvc.perform(get("/api/deadlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(created.path("id").asText()))
                .andExpect(jsonPath("$[0].remindAt").value("2035-06-11T09:00:00Z"));
        mvc.perform(get("/api/deadlines/" + created.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", notNullValue()))
                .andExpect(jsonPath("$.policyId", notNullValue()));
    }

    @Test
    void invalidReminderTimeLeavesNoPartialBusinessRows() throws Exception {
        OffsetDateTime soon = OffsetDateTime.now().plusMinutes(5);
        String body = json.writeValueAsString(new Request("잘못된 알림", soon, 10));

        mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", "invalid-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(0, count("events"));
        assertEquals(0, count("notification_policies"));
        assertEquals(0, count("reminders"));
        assertEquals(0, count("schedule_outbox"));
    }

    private int count(String table) {
        return db.queryForObject("select count(*) from " + table, Integer.class);
    }

    private record Request(String title, OffsetDateTime startsAt, int leadMinutes) { }
}
