package com.middleproject.reminder;

import com.middleproject.reminder.application.TravelRecommendationService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.FollowUpConsent;
import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceCategory;
import com.middleproject.reminder.domain.PostTripRecommendationResult;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.domain.ProviderFailure;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.TravelContextResult;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.port.TravelConsentRepository;
import com.middleproject.reminder.support.AdjustableClock;
import com.middleproject.reminder.support.FakePlaceSearchProviderPort;
import com.middleproject.reminder.support.FakeWeatherProviderPort;
import com.middleproject.reminder.support.PrivateCarFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {ReminderPlatformApplication.class, TravelRecommendationServiceIntegrationTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:travel-rec;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class TravelRecommendationServiceIntegrationTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2030, 1, 1);

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean @Primary FakeWeatherProviderPort weatherProviderPort(Clock clock) { return new FakeWeatherProviderPort(clock); }
        @Bean @Primary FakePlaceSearchProviderPort placeSearchProviderPort(Clock clock) { return new FakePlaceSearchProviderPort(clock); }
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired TravelRecommendationService recommendations;
    @Autowired TripService trips;
    @Autowired FakeWeatherProviderPort weather;
    @Autowired FakePlaceSearchProviderPort places;
    @Autowired AdjustableClock clock;
    @Autowired JdbcTemplate db;
    @Autowired TravelConsentRepository consents;

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

    // ---------- helpers ----------

    private Trip trip() {
        UUID id = PrivateCarFixtures.readyDraft(trips);
        Trip trip = trips.find(id);
        trips.confirm(id, "confirm-" + UUID.randomUUID(), "confirm-key-" + UUID.randomUUID());
        return trips.find(id);
    }

    private Trip confirmedTripWithRoute(long distanceMeters) {
        Trip trip = trip();
        PrivateCarRoute route = PrivateCarRoute.create(trip.departure(), trip.destination(), trip.departureAt(),
                com.middleproject.reminder.support.FakeGeocodingPort.SEOUL,
                com.middleproject.reminder.support.FakeGeocodingPort.BUSAN,
                (int) distanceMeters, 60, 90, 0, "fake-route", "fake", OffsetDateTime.now(clock),
                PrivateCarRoute.DEFAULT_TTL);
        db.update("insert into private_car_routes(trip_id,stable_id,origin,destination,departure_at," +
                        "origin_lat,origin_lng,destination_lat,destination_lng,distance_meters,base_duration_minutes," +
                        "traffic_duration_minutes,toll_amount,provider,source,recommended_departure_at," +
                        "reminder_lead_minutes,preview_fetched_at,preview_expires_at,created_at) " +
                        "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                trip.id(), route.stableId(), route.origin(), route.destination(), route.departureAt(),
                route.originPoint().latitude(), route.originPoint().longitude(),
                route.destinationPoint().latitude(), route.destinationPoint().longitude(),
                route.distanceMeters(), route.baseDurationMinutes(), route.trafficDurationMinutes(),
                route.tollAmount(), route.provider(), route.source(), route.recommendedDepartureAt(),
                30, route.fetchedAt(), route.expiresAt(), OffsetDateTime.now(clock));
        return trip;
    }

    private TravelContextResult context(Trip trip, DepartureTiming timing) {
        return recommendations.context(trip.id(), timing, RecommendationSort.DISTANCE);
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private List<String> names(List<PlaceCandidate> candidates) {
        return candidates.stream().map(PlaceCandidate::name).toList();
    }

    // ---------- weather dates ----------

    @Test
    void sameDayContextFetchesExactlyOneDate() {
        Trip trip = trip();
        weather.queue(DEPARTURE_DATE, new ProviderOutcome.Timeout<>());

        TravelContextResult result = context(trip, DepartureTiming.SAME_DAY);

        assertEquals(1, weather.callCount());
        assertEquals(1, weather.callCount(DEPARTURE_DATE));
        assertEquals(0, result.forecasts().size());
        assertEquals(List.of(new ProviderFailure("WEATHER", DEPARTURE_DATE.toString(), ProviderOutcome.Kind.TIMEOUT)),
                result.failures());
    }

    @Test
    void previousDayContextFetchesExactlyTwoDatesAscending() {
        Trip trip = trip();
        LocalDate previous = DEPARTURE_DATE.minusDays(1);

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(2, weather.callCount());
        assertEquals(1, weather.callCount(previous));
        assertEquals(1, weather.callCount(DEPARTURE_DATE));
        assertEquals(List.of(previous, DEPARTURE_DATE),
                result.forecasts().stream().map(com.middleproject.reminder.domain.WeatherForecast::date).toList());
        assertEquals(0, result.failures().size());
    }

    // ---------- accommodations ----------

    @Test
    void previousDayWithLongEnoughRouteSearchesAccommodations() {
        Trip trip = confirmedTripWithRoute(200_000);
        places.queue(PlaceCategory.ACCOMMODATION, new ProviderOutcome.Empty<>());

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(1, places.callCount(PlaceCategory.ACCOMMODATION));
        assertEquals(0, result.accommodations().size());
        assertEquals(List.of(new ProviderFailure("PLACE", "ACCOMMODATION", ProviderOutcome.Kind.EMPTY)),
                result.failures());
    }

    @Test
    void sameDayNeverSearchesAccommodationsEvenWithLongRoute() {
        Trip trip = confirmedTripWithRoute(300_000);

        TravelContextResult result = context(trip, DepartureTiming.SAME_DAY);

        assertEquals(0, places.callCount(PlaceCategory.ACCOMMODATION));
        assertEquals(0, result.accommodations().size());
        assertEquals(0, result.failures().size());
    }

    @Test
    void previousDayWithoutRouteNeverSearchesAccommodations() {
        Trip trip = trip();

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(0, places.callCount(PlaceCategory.ACCOMMODATION));
        assertEquals(0, result.accommodations().size());
        assertEquals(0, result.failures().size());
    }

    @Test
    void previousDayWithRouteJustUnderThresholdNeverSearchesAccommodations() {
        Trip trip = confirmedTripWithRoute(199_999);

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(0, places.callCount(PlaceCategory.ACCOMMODATION));
        assertEquals(0, result.accommodations().size());
        assertEquals(0, result.failures().size());
    }

    // ---------- partial success and typed failures ----------

    @Test
    void weatherFailureKeepsOtherForecastAndReportsTypedFailure() {
        Trip trip = trip();
        LocalDate previous = DEPARTURE_DATE.minusDays(1);
        weather.queue(previous, new ProviderOutcome.RateLimited<>());

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(1, result.forecasts().size());
        assertEquals(DEPARTURE_DATE, result.forecasts().get(0).date());
        assertEquals(List.of(new ProviderFailure("WEATHER", previous.toString(), ProviderOutcome.Kind.RATE_LIMITED)),
                result.failures());
        assertTrue(result.packingItems().contains("umbrella"));
    }

    @Test
    void accommodationFailureIsTypedAndDoesNotFailWeather() {
        Trip trip = confirmedTripWithRoute(250_000);
        places.queue(PlaceCategory.ACCOMMODATION, new ProviderOutcome.Malformed<>("bad payload"));

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(2, result.forecasts().size());
        assertEquals(List.of(new ProviderFailure("PLACE", "ACCOMMODATION", ProviderOutcome.Kind.MALFORMED)),
                result.failures());
        assertEquals(0, result.accommodations().size());
    }

    // ---------- consent row lifecycle ----------

    @Test
    void contextCreatesExactlyOneProposedRowAndKeepsIt() {
        Trip trip = trip();

        TravelContextResult first = context(trip, DepartureTiming.SAME_DAY);
        TravelContextResult second = context(trip, DepartureTiming.SAME_DAY);

        assertEquals(ConsentStatus.PROPOSED, first.consentStatus());
        assertEquals(ConsentStatus.PROPOSED, second.consentStatus());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
    }

    @Test
    void contextNeverOverwritesAcceptedOrDeclined() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), false, "consent-decline");

        TravelContextResult result = context(trip, DepartureTiming.SAME_DAY);

        assertEquals(ConsentStatus.DECLINED, result.consentStatus());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
        assertEquals(ConsentStatus.DECLINED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
    }

    @Test
    void duplicateConsentRaceIsReportedAsAbsentButUnrelatedIntegrityViolationIsRethrown() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);

        assertTrue(consents.insertProposedIfAbsent(trip.id(), "demo-owner").isEmpty());

        UUID ghostTrip = UUID.randomUUID();
        DataIntegrityViolationException integrity = assertThrows(DataIntegrityViolationException.class,
                () -> consents.insertProposedIfAbsent(ghostTrip, "demo-owner"));
        assertNotNull(integrity.getCause());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", ghostTrip));
    }

    // ---------- recommend ----------

    @Test
    void missingConsentShortCircuitsWithZeroProviderCalls() {
        Trip trip = trip();

        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.PRICE);

        assertEquals(ConsentStatus.PROPOSED, result.consentStatus());
        assertEquals(0, result.restaurants().size());
        assertEquals(0, result.attractions().size());
        assertEquals(0, result.failures().size());
        assertEquals(0, places.callCount());
    }

    @Test
    void proposedConsentShortCircuitsWithZeroProviderCalls() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);

        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.PRICE);

        assertEquals(ConsentStatus.PROPOSED, result.consentStatus());
        assertEquals(0, result.restaurants().size());
        assertEquals(0, result.attractions().size());
        assertEquals(0, places.callCount());
    }

    @Test
    void declinedConsentShortCircuitsWithZeroProviderCalls() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), false, "consent-decline-2");

        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.PRICE);

        assertEquals(ConsentStatus.DECLINED, result.consentStatus());
        assertEquals(0, result.restaurants().size());
        assertEquals(0, result.attractions().size());
        assertEquals(0, places.callCount());
    }

    @Test
    void acceptedConsentCallsRestaurantAndAttractionOnceEachAndSorts() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), true, "consent-accept");

        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.DISTANCE);

        assertEquals(1, places.callCount(PlaceCategory.RESTAURANT));
        assertEquals(1, places.callCount(PlaceCategory.ATTRACTION));
        assertEquals(0, result.failures().size());
        assertEquals(2, result.restaurants().size());
        assertEquals(List.of("Fake Cafe B", "Fake Cafe A"), names(result.restaurants()));
        assertEquals(2, result.attractions().size());
    }

    @Test
    void acceptedConsentKeepsPartialPlaceSuccessAndReportsTypedFailure() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), true, "consent-accept-2");
        places.queue(PlaceCategory.RESTAURANT, new ProviderOutcome.Timeout<>());

        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.RATING);

        assertEquals(0, result.restaurants().size());
        assertEquals(2, result.attractions().size());
        assertEquals(List.of(new ProviderFailure("PLACE", "RESTAURANT", ProviderOutcome.Kind.TIMEOUT)),
                result.failures());
    }

    // ---------- recordConsent ----------

    @Test
    void consentReplayWithSameKeyAndPayloadReturnsStoredDecision() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);

        FollowUpConsent first = recommendations.recordConsent(trip.id(), true, "consent-replay");
        FollowUpConsent replay = recommendations.recordConsent(trip.id(), true, "consent-replay");

        assertEquals(ConsentStatus.ACCEPTED, first.status());
        assertEquals(first, replay);
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
    }

    @Test
    void sameKeyWithDifferentConsentPayloadIsRejectedWithConflict() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), true, "consent-conflict");

        var conflict = assertThrows(ResponseStatusException.class,
                () -> recommendations.recordConsent(trip.id(), false, "consent-conflict"));

        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(ConsentStatus.ACCEPTED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
    }

    @Test
    void freshConsentKeyCanChangeAcceptedToDeclined() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);
        recommendations.recordConsent(trip.id(), true, "consent-accept-then-decline");

        FollowUpConsent declined = recommendations.recordConsent(trip.id(), false, "consent-accept-then-decline-2");

        assertEquals(ConsentStatus.DECLINED, declined.status());
        assertEquals(ConsentStatus.DECLINED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
    }

    // ---------- ownership ----------

    @Test
    void otherOwnerTripIsNotFoundAndCausesZeroProviderCalls() {
        UUID other = UUID.randomUUID();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                other, "other-owner", "Seoul", "Busan", PrivateCarFixtures.DEPART, null, TripStatus.DRAFT.name(),
                OffsetDateTime.now(clock), OffsetDateTime.now(clock));

        var context404 = assertThrows(ResponseStatusException.class,
                () -> recommendations.context(other, DepartureTiming.SAME_DAY, RecommendationSort.DISTANCE));
        assertEquals(404, context404.getStatusCode().value());
        var consent404 = assertThrows(ResponseStatusException.class,
                () -> recommendations.recordConsent(other, true, "consent-other"));
        assertEquals(404, consent404.getStatusCode().value());
        var recommend404 = assertThrows(ResponseStatusException.class,
                () -> recommendations.recommend(other, RecommendationSort.DISTANCE));
        assertEquals(404, recommend404.getStatusCode().value());

        assertEquals(0, weather.callCount());
        assertEquals(0, places.callCount());
        assertEquals(0, count("select count(*) from travel_recommendation_consent where trip_id=?", other));
    }

    @Test
    void blankConsentKeyIsRejectedBeforeAnyWrite() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);

        var rejected = assertThrows(ResponseStatusException.class,
                () -> recommendations.recordConsent(trip.id(), true, "  "));

        assertEquals(400, rejected.getStatusCode().value());
        assertEquals(ConsentStatus.PROPOSED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
        assertEquals(0, count("select count(*) from idempotency_record where scope like 'travel-consent:%'"));
    }

    @Test
    void oversizedConsentKeyIsRejectedAtTheServiceBoundaryBeforeAnyWrite() {
        Trip trip = trip();
        context(trip, DepartureTiming.SAME_DAY);

        var rejected = assertThrows(ResponseStatusException.class,
                () -> recommendations.recordConsent(trip.id(), true, "x".repeat(201)));

        assertEquals(400, rejected.getStatusCode().value());
        assertEquals(ConsentStatus.PROPOSED, ConsentStatus.valueOf(
                db.queryForObject("select status from travel_recommendation_consent where trip_id=?",
                        String.class, trip.id())));
        assertEquals(0, count("select count(*) from idempotency_record where scope like 'travel-consent:%'"));
    }

    // ---------- immutability of unrelated business rows ----------

    @Test
    void contextLeavesTripsRemindersOutboxAndRoutesUnchangedExceptConsentRow() {
        Trip trip = confirmedTripWithRoute(250_000);
        int tripsBefore = count("select count(*) from trips where id=?", trip.id());
        int remindersBefore = count("select count(*) from reminders where trip_id=?", trip.id());
        int outboxBefore = count("select count(*) from trip_outbox where trip_id=?", trip.id());
        int routesBefore = count("select count(*) from private_car_routes where trip_id=?", trip.id());
        int eventsBefore = count("select count(*) from trip_events where trip_id=?", trip.id());
        String routeRowBefore = db.queryForObject("select distance_meters from private_car_routes where trip_id=?",
                String.class, trip.id());

        TravelContextResult result = context(trip, DepartureTiming.PREVIOUS_DAY);

        assertEquals(tripsBefore, count("select count(*) from trips where id=?", trip.id()));
        assertEquals(remindersBefore, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(outboxBefore, count("select count(*) from trip_outbox where trip_id=?", trip.id()));
        assertEquals(routesBefore, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(eventsBefore, count("select count(*) from trip_events where trip_id=?", trip.id()));
        assertEquals(routeRowBefore, db.queryForObject("select distance_meters from private_car_routes where trip_id=?",
                String.class, trip.id()));
        assertEquals(ConsentStatus.PROPOSED, result.consentStatus());
        assertEquals(1, count("select count(*) from travel_recommendation_consent where trip_id=?", trip.id()));
    }

    // ---------- provenance ----------

    @Test
    void everyForecastAndPlaceCarriesProvenanceAndPriceRatingSources() {
        Trip trip = confirmedTripWithRoute(250_000);
        TravelContextResult context = context(trip, DepartureTiming.PREVIOUS_DAY);
        assertTrue(context.forecasts().size() >= 1);
        for (var forecast : context.forecasts()) {
            assertEquals("fake-weather", forecast.provider());
            assertEquals("fake", forecast.source());
            assertNotNull(forecast.fetchedAt());
        }
        assertTrue(context.accommodations().size() >= 1);
        for (PlaceCandidate candidate : context.accommodations()) {
            assertEquals("fake-place", candidate.provider());
            assertEquals("fake", candidate.source());
            assertNotNull(candidate.fetchedAt());
            assertNotNull(candidate.priceSource());
            assertNotNull(candidate.ratingSource());
        }

        recommendations.recordConsent(trip.id(), true, "consent-provenance");
        PostTripRecommendationResult result = recommendations.recommend(trip.id(), RecommendationSort.DISTANCE);
        for (PlaceCandidate candidate : result.restaurants()) {
            assertEquals("fake-place", candidate.provider());
            assertEquals("fake", candidate.source());
            assertNotNull(candidate.fetchedAt());
            assertNotNull(candidate.priceSource());
            assertNotNull(candidate.ratingSource());
        }
    }
}
