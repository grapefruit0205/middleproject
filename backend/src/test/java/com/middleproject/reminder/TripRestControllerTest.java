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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-rest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class TripRestControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from trip_outbox");
        db.update("delete from reminders");
        db.update("delete from notification_policies");
        db.update("delete from trip_events");
        db.update("delete from trips");
        db.update("delete from events");
    }

    private JsonNode createDraft(String key) throws Exception {
        String body = "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\"}";
        String response = mvc.perform(post("/api/trip-drafts").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    @Test
    void draftCreateConfirmCancelRoundTripThroughRest() throws Exception {
        JsonNode draft = createDraft("rest-draft-key");
        UUID id = UUID.fromString(draft.path("id").asText());
        assertEquals("DRAFT", draft.path("status").asText());
        assertEquals("demo-owner", draft.path("ownerId").asText());

        String confirmBody = "{\"confirmationId\":\"rest-confirm-1\"}";
        JsonNode confirmed = mapper.readTree(mvc.perform(post("/api/trips/" + id + "/confirm")
                        .header("Idempotency-Key", "rest-confirm-key")
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("CONFIRMED", confirmed.path("status").asText());
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));

        String cancelBody = "{\"expectedVersion\":" + confirmed.path("version").asInt() + "}";
        mvc.perform(post("/api/trips/" + id + "/cancel")
                        .header("Idempotency-Key", "rest-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON).content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=? and operation='DELETE'", Integer.class, id));
    }

    @Test
    void draftAnswerAccumulatesContext() throws Exception {
        JsonNode draft = createDraft("rest-answer-draft");
        UUID id = UUID.fromString(draft.path("id").asText());
        String answerBody = "{\"question\":\"Q1\",\"answer\":\"A1\"}";
        JsonNode answered = mapper.readTree(mvc.perform(post("/api/trips/" + id + "/draft-context")
                        .header("Idempotency-Key", "rest-answer-key")
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("A1", answered.path("draftContext").path("Q1").asText());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String body = "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\"}";
        mvc.perform(post("/api/trip-drafts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAndGetAreScopedToTheConfiguredDemoOwner() throws Exception {
        JsonNode draft = createDraft("rest-owner-draft");
        UUID id = UUID.fromString(draft.path("id").asText());
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                UUID.randomUUID(), "other-owner", "Seoul", "Busan",
                java.time.OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, "DRAFT",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        String listBody = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/trips"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, mapper.readTree(listBody).size());
        UUID other = db.queryForObject("select id from trips where owner_id='other-owner'", UUID.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/trips/" + other))
                .andExpect(status().isNotFound());
        assertEquals("demo-owner", draft.path("ownerId").asText());
    }
}
