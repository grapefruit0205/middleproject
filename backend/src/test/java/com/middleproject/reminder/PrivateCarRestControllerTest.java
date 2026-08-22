package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.support.FakeGeocodingPort;
import com.middleproject.reminder.support.FakeRouteProviderPort;
import com.middleproject.reminder.support.PrivateCarFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ReminderPlatformApplication.class, PrivateCarRestControllerTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-car-rest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class PrivateCarRestControllerTest {

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean @Primary FakeGeocodingPort geocodingPort() { return new FakeGeocodingPort(); }
        @Bean @Primary FakeRouteProviderPort routeProviderPort() { return new FakeRouteProviderPort(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TripService trips;
    @Autowired PrivateCarPlanningService planning;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        db.update("delete from private_car_routes");
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

    private UUID readyTripId() {
        UUID id = PrivateCarFixtures.draftTrip(trips);
        trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "30", "rest-lead-" + UUID.randomUUID());
        return id;
    }

    private JsonNode preview(UUID id) throws Exception {
        String body = mvc.perform(post("/api/trips/" + id + "/private-car/route-preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void nextQuestionAndPreviewRoundTrip() throws Exception {
        UUID id = readyTripId();
        // after answering the lead the input is complete, so next-question is null
        mvc.perform(get("/api/trips/" + id + "/private-car/next-question"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").isEmpty());
        UUID unanswered = PrivateCarFixtures.draftTrip(trips);
        mvc.perform(get("/api/trips/" + unanswered + "/private-car/next-question"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value("private_car.reminder_lead_minutes"));
        JsonNode preview = preview(id);
        assertEquals("fake", preview.path("source").asText());
        assertEquals(FakeGeocodingPort.SEOUL.latitude(), preview.path("originPoint").path("latitude").asDouble(), 0.0001);
        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
    }

    @Test
    void confirmPersistsRouteAndReminder() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = preview(id);
        String body = "{\"proposalId\":\"" + preview.path("stableId").asText()
                + "\",\"previewFetchedAt\":\"" + preview.path("fetchedAt").asText()
                + "\",\"reminderLeadMinutes\":30,\"confirmationId\":\"rest-confirm-1\"}";
        mvc.perform(post("/api/trips/" + id + "/private-car/confirm")
                        .header("Idempotency-Key", "rest-confirm-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reminderLeadMinutes").value(30));
        assertEquals(1, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", Integer.class, id));
    }

    @Test
    void confirmRejectsInvalidLeadAndFuturePreviewTimestamp() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = preview(id);
        String staleBody = "{\"proposalId\":\"" + preview.path("stableId").asText()
                + "\",\"previewFetchedAt\":\"" + preview.path("fetchedAt").asText()
                + "\",\"reminderLeadMinutes\":1441,\"confirmationId\":\"rest-bad-lead\"}";
        mvc.perform(post("/api/trips/" + id + "/private-car/confirm")
                        .header("Idempotency-Key", "rest-bad-lead-key")
                        .contentType(MediaType.APPLICATION_JSON).content(staleBody))
                .andExpect(status().isBadRequest());
        String futureBody = "{\"proposalId\":\"" + preview.path("stableId").asText()
                + "\",\"previewFetchedAt\":\"2999-01-01T00:00:00+09:00"
                + "\",\"reminderLeadMinutes\":30,\"confirmationId\":\"rest-future\"}";
        mvc.perform(post("/api/trips/" + id + "/private-car/confirm")
                        .header("Idempotency-Key", "rest-future-key")
                        .contentType(MediaType.APPLICATION_JSON).content(futureBody))
                .andExpect(status().isBadRequest());
        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trips where id=? and status='DRAFT'", Integer.class, id));
    }

    @Test
    void confirmRejectsMalformedProposalIdBeforeAnyProviderOrBusinessWrite() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = preview(id);
        String fetchedAt = preview.path("fetchedAt").asText();
        String[] malformed = {
                "not-a-hex-id",
                "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                "0".repeat(64) + "0"
        };
        for (String proposalId : malformed) {
            String body = "{\"proposalId\":\"" + proposalId
                    + "\",\"previewFetchedAt\":\"" + fetchedAt
                    + "\",\"reminderLeadMinutes\":30,\"confirmationId\":\"rest-malformed-"
                    + proposalId.length() + "\"}";
            mvc.perform(post("/api/trips/" + id + "/private-car/confirm")
                            .header("Idempotency-Key", "rest-malformed-key")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trips where id=? and status='DRAFT'", Integer.class, id));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = preview(id);
        String body = "{\"proposalId\":\"" + preview.path("stableId").asText()
                + "\",\"previewFetchedAt\":\"" + preview.path("fetchedAt").asText()
                + "\",\"reminderLeadMinutes\":30,\"confirmationId\":\"rest-no-key\"}";
        mvc.perform(post("/api/trips/" + id + "/private-car/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
