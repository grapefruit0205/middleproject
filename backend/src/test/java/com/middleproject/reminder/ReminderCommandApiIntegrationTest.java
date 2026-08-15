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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminder-command-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class ReminderCommandApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void reset() {
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @Test
    void parsesNormalRequestWithSeoulSchedule() throws Exception {
        mvc.perform(post("/api/reminder-commands/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"remind me to submit report tomorrow at 15:00\",\"referenceDate\":\"2030-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.command.title").value("submit report"))
                .andExpect(jsonPath("$.command.scheduledAt").value("2030-01-02T15:00:00+09:00"))
                .andExpect(jsonPath("$.command.timezone").value("Asia/Seoul"));
    }

    @Test
    void omittedReferenceDateUsesSeoulClock() throws Exception {
        var controller = new com.middleproject.reminder.web.ReminderCommandController(
                new com.middleproject.reminder.infrastructure.parsing.DeterministicReminderCommandParser(),
                Clock.fixed(Instant.parse("2030-01-01T15:30:00Z"), ZoneId.of("UTC")));
        var response = controller.parse(new com.middleproject.reminder.web.ReminderCommandController.Request(
                "remind me to submit report tomorrow at 9:00", null));
        assertEquals("2030-01-03T09:00+09:00", response.getBody().command().scheduledAt().toString());
    }

    @Test
    void parserFailureReturns422WithoutChangingAggregateTables() throws Exception {
        Map<String, Integer> before = counts();
        mvc.perform(post("/api/reminder-commands/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"remind me to submit report tomorrow\",\"referenceDate\":\"2030-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("PARSER_FAILED"));
        assertEquals(before, counts());
    }

    @Test
    void businessInvalidReturns422WithoutChangingAggregateTables() throws Exception {
        Map<String, Integer> before = counts();
        mvc.perform(post("/api/reminder-commands/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"remind me to submit report tomorrow at 25:00\",\"referenceDate\":\"2030-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("BUSINESS_INVALID"));
        assertEquals(before, counts());
    }

    private Map<String, Integer> counts() {
        return Map.of(
                "events", count("events"),
                "notification_policies", count("notification_policies"),
                "reminders", count("reminders"),
                "idempotency_record", count("idempotency_record"));
    }

    private int count(String table) {
        return db.queryForObject("select count(*) from " + table, Integer.class);
    }
}
