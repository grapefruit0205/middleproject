package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.support.AdjustableClock;
import com.middleproject.reminder.support.FakePlaceSearchProviderPort;
import com.middleproject.reminder.support.FakeWeatherProviderPort;
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

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ReminderPlatformApplication.class, TravelRecommendationMcpIntegrationTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:travel-mcp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class TravelRecommendationMcpIntegrationTest {

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean @Primary FakeWeatherProviderPort weatherProviderPort(Clock clock) { return new FakeWeatherProviderPort(clock); }
        @Bean @Primary FakePlaceSearchProviderPort placeSearchProviderPort(Clock clock) { return new FakePlaceSearchProviderPort(clock); }
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TripService trips;
    @Autowired FakeWeatherProviderPort weather;
    @Autowired FakePlaceSearchProviderPort places;
    @Autowired AdjustableClock clock;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        db.update("delete from travel_recommendation_consent");
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
        weather.reset();
        places.reset();
    }

    private Trip trip() {
        UUID id = PrivateCarFixtures.readyDraft(trips);
        trips.confirm(id, "confirm-" + UUID.randomUUID(), "confirm-key-" + UUID.randomUUID());
        return trips.find(id);
    }

    private JsonNode call(String body) throws Exception {
        return call(body, "alice");
    }

    private JsonNode call(String body, String user) throws Exception {
        return call(body, user, null);
    }

    private JsonNode call(String body, String user, String mcpUser) throws Exception {
        var request = post("/api/mcp").contentType(MediaType.APPLICATION_JSON).content(body);
        if (user != null) request.principal(() -> user);
        if (mcpUser != null) request.header("X-User-Id", mcpUser);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String tool(String name, String args) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + args + "}}";
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    @Test
    void travelToolsAreExposedWithClosedSchemasAndEnums() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(t -> names.add(t.path("name").asText()));
        for (String expected : new String[]{"get_trip_travel_context", "record_trip_followup_consent", "get_trip_recommendations"}) {
            assertTrue(names.contains(expected), expected + " must be exposed");
            JsonNode schema = null;
            for (JsonNode t : tools) if (t.path("name").asText().equals(expected)) schema = t.path("inputSchema");
            assertEquals("object", schema.path("type").asText());
            assertFalse(schema.path("additionalProperties").asBoolean());
            schema.path("properties").fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (field.getKey().equals("tripId")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("uuid", value.path("format").asText());
                }
                if (field.getKey().equals("accepted")) assertEquals("boolean", value.path("type").asText());
                if (field.getKey().equals("departureTiming")) {
                    assertEquals("string", value.path("type").asText());
                    JsonNode enums = value.path("enum");
                    assertEquals("SAME_DAY", enums.get(0).asText());
                    assertEquals("PREVIOUS_DAY", enums.get(1).asText());
                    assertEquals(2, enums.size());
                }
                if (field.getKey().equals("sort")) {
                    assertEquals("string", value.path("type").asText());
                    JsonNode enums = value.path("enum");
                    assertEquals("DISTANCE", enums.get(0).asText());
                    assertEquals("PRICE", enums.get(1).asText());
                    assertEquals("RATING", enums.get(2).asText());
                    assertEquals(3, enums.size());
                }
                if (field.getKey().equals("idempotencyKey")) {
                    assertEquals(1, value.path("minLength").asInt());
                    assertEquals(200, value.path("maxLength").asInt());
                }
            });
            assertEquals(schema.path("properties").size(), schema.path("required").size());
        }
    }

    @Test
    void travelToolsContextConsentRecommendFlowMatchesService() throws Exception {
        Trip trip = trip();
        JsonNode context = call(tool("get_trip_travel_context", "{\"tripId\":\"" + trip.id() + "\",\"departureTiming\":\"PREVIOUS_DAY\",\"sort\":\"DISTANCE\"}"));
        assertEquals(trip.id().toString(), context.path("result").path("structuredContent").path("tripId").asText());
        assertEquals("PREVIOUS_DAY", context.path("result").path("structuredContent").path("departureTiming").asText());
        assertEquals(2, context.path("result").path("structuredContent").path("forecasts").size());
        assertEquals("PROPOSED", context.path("result").path("structuredContent").path("consentStatus").asText());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));

        JsonNode consent = call(tool("record_trip_followup_consent", "{\"tripId\":\"" + trip.id() + "\",\"accepted\":true,\"idempotencyKey\":\"mcp-consent-key\"}"));
        assertEquals("ACCEPTED", consent.path("result").path("structuredContent").path("status").asText());

        JsonNode replay = call(tool("record_trip_followup_consent", "{\"tripId\":\"" + trip.id() + "\",\"accepted\":true,\"idempotencyKey\":\"mcp-consent-key\"}"));
        assertEquals(consent.path("result").path("structuredContent").toString(), replay.path("result").path("structuredContent").toString());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
        assertEquals(ConsentStatus.ACCEPTED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?", String.class, trip.id())));

        JsonNode changed = call(tool("record_trip_followup_consent", "{\"tripId\":\"" + trip.id() + "\",\"accepted\":false,\"idempotencyKey\":\"mcp-consent-key-2\"}"));
        assertEquals("DECLINED", changed.path("result").path("structuredContent").path("status").asText());
        assertEquals(ConsentStatus.DECLINED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?", String.class, trip.id())));

        JsonNode recommendations = call(tool("get_trip_recommendations", "{\"tripId\":\"" + trip.id() + "\",\"sort\":\"DISTANCE\"}"));
        assertEquals("DECLINED", recommendations.path("result").path("structuredContent").path("consentStatus").asText());
        assertEquals(0, recommendations.path("result").path("structuredContent").path("restaurants").size());
    }

    @Test
    void travelToolValidationRejectsMissingUnknownWrongTypeInvalidEnumAndKeyBounds() throws Exception {
        Trip trip = trip();
        String tripId = trip.id().toString();
        String[][] cases = {
                {"get_trip_travel_context", "{\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"sort\":\"DISTANCE\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"departureTiming\":\"SAME_DAY\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"departureTiming\":\"TOMORROW\",\"sort\":\"DISTANCE\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"CHEAPEST\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"departureTiming\":1,\"sort\":\"DISTANCE\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"not-a-uuid\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"},
                {"get_trip_travel_context", "{\"tripId\":\"" + tripId + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\",\"extra\":true}"},
                {"record_trip_followup_consent", "{\"accepted\":true,\"idempotencyKey\":\"k\"}"},
                {"record_trip_followup_consent", "{\"tripId\":\"" + tripId + "\",\"idempotencyKey\":\"k\"}"},
                {"record_trip_followup_consent", "{\"tripId\":\"" + tripId + "\",\"accepted\":\"yes\",\"idempotencyKey\":\"k\"}"},
                {"record_trip_followup_consent", "{\"tripId\":\"" + tripId + "\",\"accepted\":true,\"idempotencyKey\":\"   \"}"},
                {"record_trip_followup_consent", "{\"tripId\":\"" + tripId + "\",\"accepted\":true,\"idempotencyKey\":\"" + "x".repeat(201) + "\"}"},
                {"record_trip_followup_consent", "{\"tripId\":\"" + tripId + "\",\"accepted\":true}"},
                {"get_trip_recommendations", "{}"},
                {"get_trip_recommendations", "{\"tripId\":\"" + tripId + "\",\"sort\":\"NEAREST\"}"},
                {"get_trip_recommendations", "{\"tripId\":\"bad\",\"sort\":\"DISTANCE\"}"},
        };
        for (String[] c : cases) {
            JsonNode response = call(tool(c[0], c[1]));
            assertEquals(-32602, response.path("error").path("code").asInt(), c[0] + " " + c[1]);
        }
        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from idempotency_record where scope like 'travel-consent:%'"));
    }

    @Test
    void travelToolsRejectUnknownTripWithBusinessErrorAndNoProviderCalls() throws Exception {
        UUID missing = UUID.randomUUID();
        JsonNode context = call(tool("get_trip_travel_context", "{\"tripId\":\"" + missing + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"));
        assertTrue(context.has("error"));
        assertFalse(context.toString().contains(missing.toString()), "trip id must not leak into the error");
        JsonNode consent = call(tool("record_trip_followup_consent", "{\"tripId\":\"" + missing + "\",\"accepted\":true,\"idempotencyKey\":\"unknown-key\"}"));
        assertTrue(consent.has("error"));
        JsonNode recommendations = call(tool("get_trip_recommendations", "{\"tripId\":\"" + missing + "\",\"sort\":\"DISTANCE\"}"));
        assertTrue(recommendations.has("error"));
        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
    }

    @Test
    void travelToolsRejectOtherOwnerTripWithoutDisclosure() throws Exception {
        UUID other = UUID.randomUUID();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                other, "other-owner", "Seoul", "Busan", PrivateCarFixtures.DEPART, null, TripStatus.DRAFT.name(),
                OffsetDateTime.now(clock), OffsetDateTime.now(clock));

        JsonNode context = call(tool("get_trip_travel_context", "{\"tripId\":\"" + other + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"));
        assertTrue(context.has("error"));
        assertFalse(context.toString().contains(other.toString()), "other-owner trip id must not leak");
        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", other));
    }

    @Test
    void travelToolSuccessAndFailureAreAuditedWithToolNameAndOutcome() throws Exception {
        Trip trip = trip();
        JsonNode context = call(tool("get_trip_travel_context", "{\"tripId\":\"" + trip.id() + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"));
        assertFalse(context.has("error"));
        assertEquals(1, count("select count(*) from mcp_audit where tool_name='get_trip_travel_context' and user_id='alice' and outcome='SUCCEEDED'"));

        JsonNode failed = call(tool("record_trip_followup_consent", "{\"tripId\":\"" + trip.id() + "\",\"accepted\":true,\"idempotencyKey\":\"   \"}"));
        assertEquals(-32602, failed.path("error").path("code").asInt());
        assertEquals(1, count("select count(*) from mcp_audit where tool_name='record_trip_followup_consent' and user_id='alice' and outcome='FAILED'"));

        JsonNode unknown = call(tool("get_trip_recommendations", "{\"tripId\":\"" + UUID.randomUUID() + "\",\"sort\":\"DISTANCE\"}"));
        assertTrue(unknown.has("error"));
        assertEquals(1, count("select count(*) from mcp_audit where tool_name='get_trip_recommendations' and user_id='alice' and outcome='FAILED'"));
    }

    @Test
    void travelToolsAreAuditedWithPrincipalEvenWhenXUserIdDisagrees() throws Exception {
        Trip trip = trip();
        JsonNode context = call(tool("get_trip_travel_context", "{\"tripId\":\"" + trip.id() + "\",\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"), "alice", "mallory");
        assertFalse(context.has("error"));
        assertEquals(1, count("select count(*) from mcp_audit where tool_name='get_trip_travel_context' and user_id='alice' and outcome='SUCCEEDED'"));
        assertEquals(0, count("select count(*) from mcp_audit where user_id='mallory'"));
    }
}
