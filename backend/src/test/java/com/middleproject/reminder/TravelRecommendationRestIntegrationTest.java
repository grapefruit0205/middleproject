package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.TravelRecommendationService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ReminderPlatformApplication.class, TravelRecommendationRestIntegrationTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:travel-rest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class TravelRecommendationRestIntegrationTest {

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
    @Autowired TravelRecommendationService recommendations;
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

    private JsonNode postJson(String path, String body, String key) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private void otherOwnerTrip() {
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                UUID.randomUUID(), "other-owner", "Seoul", "Busan", PrivateCarFixtures.DEPART, null, TripStatus.DRAFT.name(),
                OffsetDateTime.now(clock), OffsetDateTime.now(clock));
    }

    @Test
    void contextConsentRecommendHappyPathThroughRest() throws Exception {
        Trip trip = trip();

        JsonNode context = postJson("/api/trips/" + trip.id() + "/travel/context",
                "{\"departureTiming\":\"PREVIOUS_DAY\",\"sort\":\"DISTANCE\"}", null);
        assertEquals(trip.id().toString(), context.path("tripId").asText());
        assertEquals("PREVIOUS_DAY", context.path("departureTiming").asText());
        assertEquals("DISTANCE", context.path("sort").asText());
        assertEquals(2, context.path("forecasts").size());
        assertTrue(context.path("packingItems").size() > 0);
        assertEquals(0, context.path("accommodations").size());
        assertEquals(0, context.path("failures").size());
        assertEquals("PROPOSED", context.path("consentStatus").asText());

        JsonNode consent = postJson("/api/trips/" + trip.id() + "/travel/consent",
                "{\"accepted\":true}", "rest-consent-key");
        assertEquals("ACCEPTED", consent.path("status").asText());
        assertEquals(ConsentStatus.ACCEPTED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));

        JsonNode changed = postJson("/api/trips/" + trip.id() + "/travel/consent",
                "{\"accepted\":false}", "rest-consent-key-2");
        assertEquals("DECLINED", changed.path("status").asText());
        assertEquals(ConsentStatus.DECLINED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));

        JsonNode recommendations = getJson("/api/trips/" + trip.id() + "/travel/recommendations?sort=DISTANCE");
        assertEquals("DECLINED", recommendations.path("consentStatus").asText());
        assertEquals("DISTANCE", recommendations.path("sort").asText());
        assertEquals(0, recommendations.path("restaurants").size());
        assertEquals(0, recommendations.path("attractions").size());
        assertEquals(0, recommendations.path("failures").size());
    }

    @Test
    void consentSameKeyReplayReturnsStoredDecisionExactlyOnce() throws Exception {
        Trip trip = trip();
        postJson("/api/trips/" + trip.id() + "/travel/context",
                "{\"departureTiming\":\"SAME_DAY\",\"sort\":\"RATING\"}", null);

        JsonNode first = postJson("/api/trips/" + trip.id() + "/travel/consent",
                "{\"accepted\":true}", "replay-key");
        JsonNode replay = postJson("/api/trips/" + trip.id() + "/travel/consent",
                "{\"accepted\":true}", "replay-key");
        assertEquals(first.toString(), replay.toString());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
    }

    @Test
    void malformedAndUnknownEnumsAndBooleansAreRejectedWith400() throws Exception {
        Trip trip = trip();

        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":\"TOMORROW\",\"sort\":\"DISTANCE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":\"SAME_DAY\",\"sort\":\"CHEAPEST\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":\"SAME_DAY\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sort\":\"DISTANCE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":1,\"sort\":\"DISTANCE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\",\"extra\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":\"yes\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "k4")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "k5")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true,\"extra\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/trips/" + trip.id() + "/travel/recommendations?sort=NEAREST"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trips/" + trip.id() + "/travel/recommendations"))
                .andExpect(status().isBadRequest());

        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from idempotency_record where scope like 'travel-consent:%'"));
    }

    @Test
    void consentKeyMustBeNonblankAndAtMost200Characters() throws Exception {
        Trip trip = trip();
        postJson("/api/trips/" + trip.id() + "/travel/context",
                "{\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}", null);

        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent").header("Idempotency-Key", "x".repeat(201))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/" + trip.id() + "/travel/consent")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isBadRequest());

        assertEquals(ConsentStatus.PROPOSED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
        assertEquals(0, count("select count(*) from idempotency_record where scope like 'travel-consent:%'"));
    }

    @Test
    void otherOwnerTripIsNotFoundOnAllThreeEndpoints() throws Exception {
        otherOwnerTrip();
        UUID other = db.queryForObject("select id from trips where owner_id='other-owner'", UUID.class);

        mvc.perform(post("/api/trips/" + other + "/travel/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureTiming\":\"SAME_DAY\",\"sort\":\"DISTANCE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/trips/" + other + "/travel/consent").header("Idempotency-Key", "other-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/" + other + "/travel/recommendations?sort=DISTANCE"))
                .andExpect(status().isNotFound());

        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", other));
    }

}
