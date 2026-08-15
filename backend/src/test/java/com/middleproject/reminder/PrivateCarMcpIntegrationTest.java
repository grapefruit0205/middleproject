package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ReminderPlatformApplication.class, PrivateCarMcpIntegrationTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-car-mcp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class PrivateCarMcpIntegrationTest {

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
        db.update("delete from mcp_audit");
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

    private JsonNode call(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content(body).principal(() -> "alice"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String tool(String name, String args) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + args + "}}";
    }

    private UUID readyTripId() {
        UUID id = PrivateCarFixtures.draftTrip(trips);
        trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "30", "mcp-lead-" + UUID.randomUUID());
        return id;
    }

    @Test
    void privateCarToolsAreExposedWithCorrectSchema() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(t -> names.add(t.path("name").asText()));
        for (String expected : new String[]{"next_private_car_question", "preview_private_car_route", "confirm_private_car_route"}) {
            assertTrue(names.contains(expected), expected + " must be exposed");
            JsonNode schema = null;
            for (JsonNode t : tools) if (t.path("name").asText().equals(expected)) schema = t.path("inputSchema");
            assertEquals("object", schema.path("type").asText());
            assertFalse(schema.path("additionalProperties").asBoolean());
            schema.path("properties").fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (field.getKey().equals("reminderLeadMinutes")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(0, value.path("minimum").asInt());
                    assertEquals(1440, value.path("maximum").asInt());
                } else {
                    assertEquals("string", value.path("type").asText());
                    if (field.getKey().equals("previewFetchedAt")) assertEquals("date-time", value.path("format").asText());
                    if (field.getKey().equals("tripId")) assertEquals("uuid", value.path("format").asText());
                    if (field.getKey().equals("proposalId")) {
                        assertEquals("[0-9a-f]{64}", value.path("pattern").asText());
                        assertEquals(64, value.path("minLength").asInt());
                        assertEquals(64, value.path("maxLength").asInt());
                        assertFalse(value.has("format"), "proposalId must not be marked as a UUID");
                    }
                    if (field.getKey().equals("confirmationId"))
                        assertFalse(value.has("format"), "confirmationId must not be marked as a UUID");
                }
            });
            assertEquals(schema.path("properties").size(), schema.path("required").size());
        }
    }

    @Test
    void nextAndPreviewMatchServiceResultsExactly() throws Exception {
        UUID id = readyTripId();
        JsonNode next = call(tool("next_private_car_question", "{\"tripId\":\"" + id + "\"}"));
        assertTrue(next.path("result").path("structuredContent").isNull());
        JsonNode preview = call(tool("preview_private_car_route", "{\"tripId\":\"" + id + "\"}"));
        assertEquals("fake", preview.path("result").path("structuredContent").path("source").asText());
        assertEquals("fake", planning.previewRoute(id).source());
        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));

        UUID unanswered = PrivateCarFixtures.draftTrip(trips);
        JsonNode nextQuestion = call(tool("next_private_car_question", "{\"tripId\":\"" + unanswered + "\"}"));
        assertEquals("private_car.reminder_lead_minutes", nextQuestion.path("result").path("structuredContent").asText());
    }

    @Test
    void confirmToolPersistsExactOnceAndMatchesService() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = call(tool("preview_private_car_route", "{\"tripId\":\"" + id + "\"}"));
        String stableId = preview.path("result").path("structuredContent").path("stableId").asText();
        String fetchedAt = preview.path("result").path("structuredContent").path("fetchedAt").asText();
        String confirmArgs = "{\"tripId\":\"" + id + "\",\"proposalId\":\"" + stableId
                + "\",\"previewFetchedAt\":\"" + fetchedAt + "\",\"reminderLeadMinutes\":45"
                + ",\"confirmationId\":\"mcp-confirm-1\",\"idempotencyKey\":\"mcp-confirm-key-1\"}";
        JsonNode first = call(tool("confirm_private_car_route", confirmArgs));
        JsonNode replay = call(tool("confirm_private_car_route", confirmArgs));
        JsonNode firstTrip = first.path("result").path("structuredContent").path("trip");
        JsonNode replayTrip = replay.path("result").path("structuredContent").path("trip");
        assertEquals("CONFIRMED", firstTrip.path("status").asText());
        assertEquals("CONFIRMED", replayTrip.path("status").asText());
        // Idempotent replay must return the same business facts; timestamps are compared as
        // instants because the first live response keeps offsets like +09:00 while the
        // deserialized replay normalizes to Z (e.g. 2030-01-01T01:00:00Z).
        assertEquals(firstTrip.path("id").asText(), replayTrip.path("id").asText());
        assertEquals(firstTrip.path("version").asLong(), replayTrip.path("version").asLong());
        assertEquals(firstTrip.path("confirmationId").asText(), replayTrip.path("confirmationId").asText());
        assertEquals(first.path("result").path("structuredContent").path("route").path("stableId").asText(),
                replay.path("result").path("structuredContent").path("route").path("stableId").asText());
        assertEquals(first.path("result").path("structuredContent").path("route").path("distanceMeters").asInt(),
                replay.path("result").path("structuredContent").path("route").path("distanceMeters").asInt());
        assertEquals(first.path("result").path("structuredContent").path("route").path("baseDurationMinutes").asInt(),
                replay.path("result").path("structuredContent").path("route").path("baseDurationMinutes").asInt());
        assertEquals(first.path("result").path("structuredContent").path("route").path("trafficDurationMinutes").asInt(),
                replay.path("result").path("structuredContent").path("route").path("trafficDurationMinutes").asInt());
        assertEquals(first.path("result").path("structuredContent").path("route").path("tollAmount").asInt(),
                replay.path("result").path("structuredContent").path("route").path("tollAmount").asInt());
        assertEquals(first.path("result").path("structuredContent").path("reminderLeadMinutes").asInt(),
                replay.path("result").path("structuredContent").path("reminderLeadMinutes").asInt());
        JsonNode firstFetched = first.path("result").path("structuredContent").path("route").path("fetchedAt");
        JsonNode replayFetched = replay.path("result").path("structuredContent").path("route").path("fetchedAt");
        assertEquals(OffsetDateTime.parse(firstFetched.asText()).toInstant(),
                OffsetDateTime.parse(replayFetched.asText()).toInstant());
        JsonNode firstDeparture = firstTrip.path("departureAt");
        JsonNode replayDeparture = replayTrip.path("departureAt");
        assertEquals(OffsetDateTime.parse(firstDeparture.asText()).toInstant(),
                OffsetDateTime.parse(replayDeparture.asText()).toInstant());
        assertEquals(1, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", Integer.class, id));
        assertEquals(45, db.queryForObject("select reminder_lead_minutes from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(first.path("result").path("structuredContent").path("reminderLeadMinutes").asInt(),
                planning.confirmRoute(id, stableId, java.time.OffsetDateTime.parse(fetchedAt), 45, "mcp-confirm-1", "mcp-confirm-key-1").reminderLeadMinutes());
    }

    @Test
    void confirmRejectsInvalidLeadAndInvalidDateAndPersistsNothing() throws Exception {
        UUID id = readyTripId();
        JsonNode preview = call(tool("preview_private_car_route", "{\"tripId\":\"" + id + "\"}"));
        String stableId = preview.path("result").path("structuredContent").path("stableId").asText();
        String fetchedAt = preview.path("result").path("structuredContent").path("fetchedAt").asText();

        JsonNode badLead = call(tool("confirm_private_car_route", "{\"tripId\":\"" + id + "\",\"proposalId\":\"" + stableId
                + "\",\"previewFetchedAt\":\"" + fetchedAt + "\",\"reminderLeadMinutes\":1441"
                + ",\"confirmationId\":\"mcp-bad-lead\",\"idempotencyKey\":\"mcp-bad-lead-key\"}"));
        assertEquals(-32602, badLead.path("error").path("code").asInt());

        JsonNode badDate = call(tool("confirm_private_car_route", "{\"tripId\":\"" + id + "\",\"proposalId\":\"" + stableId
                + "\",\"previewFetchedAt\":\"not-a-date\",\"reminderLeadMinutes\":30"
                + ",\"confirmationId\":\"mcp-bad-date\",\"idempotencyKey\":\"mcp-bad-date-key\"}"));
        assertEquals(-32602, badDate.path("error").path("code").asInt());

        JsonNode futureDate = call(tool("confirm_private_car_route", "{\"tripId\":\"" + id + "\",\"proposalId\":\"" + stableId
                + "\",\"previewFetchedAt\":\"2999-01-01T00:00:00+09:00\",\"reminderLeadMinutes\":30"
                + ",\"confirmationId\":\"mcp-future\",\"idempotencyKey\":\"mcp-future-key\"}"));
        assertTrue(futureDate.has("error"));

        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals("DRAFT", db.queryForObject("select status from trips where id=?", String.class, id));
    }

    @Test
    void confirmRejectsMalformedProposalIdWithInvalidParamsBeforeProviderOrBusinessWrite() throws Exception {
        UUID id = readyTripId();
        String fetchedAt = "2030-01-01T00:00:00+09:00";
        String[] malformed = {
                "not-a-hex-id",
                "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                "0".repeat(64) + "0"
        };
        for (String proposalId : malformed) {
            JsonNode response = call(tool("confirm_private_car_route", "{\"tripId\":\"" + id
                    + "\",\"proposalId\":\"" + proposalId
                    + "\",\"previewFetchedAt\":\"" + fetchedAt + "\",\"reminderLeadMinutes\":30"
                    + ",\"confirmationId\":\"mcp-malformed\",\"idempotencyKey\":\"mcp-malformed-key\"}"));
            assertEquals(-32602, response.path("error").path("code").asInt());
        }
        assertEquals(0, db.queryForObject("select count(*) from private_car_routes where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, id));
        assertEquals(0, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, id));
        assertEquals("DRAFT", db.queryForObject("select status from trips where id=?", String.class, id));
    }

    @Test
    void privateCarToolsDispatchWithoutTouchingReminderUuidAccess() throws Exception {
        UUID id = readyTripId();
        JsonNode next = call(tool("next_private_car_question", "{\"tripId\":\"" + id + "\"}"));
        assertTrue(!next.has("error"));
        JsonNode preview = call(tool("preview_private_car_route", "{\"tripId\":\"" + id + "\"}"));
        assertTrue(!preview.has("error"));
        // an unknown trip id must produce a business error, not an RPC/validation crash
        JsonNode missing = call(tool("next_private_car_question", "{\"tripId\":\"" + UUID.randomUUID() + "\"}"));
        assertTrue(missing.has("error"));
    }
}
