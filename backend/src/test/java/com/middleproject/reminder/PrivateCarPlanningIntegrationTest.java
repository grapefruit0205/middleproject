package com.middleproject.reminder;

import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.support.AdjustableClock;
import com.middleproject.reminder.support.FakeGeocodingPort;
import com.middleproject.reminder.support.FakeRouteProviderPort;
import com.middleproject.reminder.support.PrivateCarFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {ReminderPlatformApplication.class, PrivateCarPlanningIntegrationTest.FakeProviderConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-car;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class PrivateCarPlanningIntegrationTest {

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean @Primary FakeGeocodingPort geocodingPort() { return new FakeGeocodingPort(); }
        @Bean @Primary FakeRouteProviderPort routeProviderPort() { return new FakeRouteProviderPort(); }
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired PrivateCarPlanningService planning;
    @Autowired TripService trips;
    @Autowired FakeGeocodingPort geocoding;
    @Autowired FakeRouteProviderPort routes;
    @Autowired AdjustableClock clock;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
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
        geocoding.reset();
        routes.reset();
    }

    private Trip readyTrip() {
        UUID id = PrivateCarFixtures.readyDraft(trips);
        return trips.find(id);
    }

    private PrivateCarPlanningService.ConfirmationResult confirm(Trip trip, String proposalId, OffsetDateTime fetchedAt, String key) {
        return planning.confirmRoute(trip.id(), proposalId, fetchedAt, 30, "confirm-" + UUID.randomUUID(), key);
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    @Test
    void nextQuestionWalksStableOrderAndCompletes() {
        UUID id = PrivateCarFixtures.draftTrip(trips);
        Trip trip = trips.find(id);
        assertNotNull(trip.departure());
        assertNotNull(trip.destination());
        assertNotNull(trip.departureAt());
        assertEquals("private_car.reminder_lead_minutes", planning.nextQuestion(id));
        trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "30", "walk-lead");
        assertEquals(null, planning.nextQuestion(id));
        assertEquals(1, count("select count(*) from trips where id=?", id));
    }

    @Test
    void routePreviewPersistsNothingAndReturnsStableProposalWithProvenance() {
        Trip trip = readyTrip();
        int eventsBefore = count("select count(*) from trip_events where trip_id=?", trip.id());
        int routesBefore = count("select count(*) from private_car_routes where trip_id=?", trip.id());
        int remindersBefore = count("select count(*) from reminders where trip_id=?", trip.id());
        int outboxBefore = count("select count(*) from trip_outbox where trip_id=?", trip.id());
        OffsetDateTime before = OffsetDateTime.now(clock);
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        assertNotNull(preview.stableId());
        assertEquals("fake", preview.source());
        assertEquals(FakeGeocodingPort.SEOUL, preview.originPoint());
        assertEquals(FakeGeocodingPort.BUSAN, preview.destinationPoint());
        assertTrue(!preview.fetchedAt().isBefore(before));
        assertTrue(preview.expiresAt().isAfter(preview.fetchedAt()));
        assertEquals(eventsBefore, count("select count(*) from trip_events where trip_id=?", trip.id()));
        assertEquals(routesBefore, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(remindersBefore, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(outboxBefore, count("select count(*) from trip_outbox where trip_id=?", trip.id()));
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
    }

    @Test
    void invalidLeadAnswerIsRejectedAndPersistsNothing() {
        UUID id = PrivateCarFixtures.draftTrip(trips);
        assertThrows(ResponseStatusException.class,
                () -> trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "-1", "bad-lead-1"));
        assertThrows(ResponseStatusException.class,
                () -> trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "1441", "bad-lead-2"));
        assertThrows(ResponseStatusException.class,
                () -> trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "not-a-number", "bad-lead-3"));
        Trip trip = trips.find(id);
        assertTrue(!trip.draftContext().containsKey(PrivateCarPlanningInput.LEAD_KEY));
        assertEquals("private_car.reminder_lead_minutes", planning.nextQuestion(id));
        assertEquals(1, count("select count(*) from trips where id=?", id));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", id));
        assertEquals(0, count("select count(*) from trip_events where trip_id=? and type='DRAFT_ANSWERED'", id));
    }

    @Test
    void providerFailurePersistsNothing() {
        Trip trip = readyTrip();
        int eventsBefore = count("select count(*) from trip_events where trip_id=?", trip.id());
        int routesBefore = count("select count(*) from private_car_routes where trip_id=?", trip.id());
        int remindersBefore = count("select count(*) from reminders where trip_id=?", trip.id());
        int outboxBefore = count("select count(*) from trip_outbox where trip_id=?", trip.id());
        routes.failWith(new ProviderOutcome.Timeout<>());
        assertThrows(ResponseStatusException.class, () -> planning.previewRoute(trip.id()));
        geocoding.failWith(new ProviderOutcome.Malformed<>("bad coordinates"));
        assertThrows(ResponseStatusException.class, () -> planning.previewRoute(trip.id()));
        assertEquals(routesBefore, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(eventsBefore, count("select count(*) from trip_events where trip_id=?", trip.id()));
        assertEquals(remindersBefore, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(outboxBefore, count("select count(*) from trip_outbox where trip_id=?", trip.id()));
    }

    @Test
    void retryableProviderOutcomeIsRetriedAtMostOnce() {
        Trip trip = readyTrip();
        routes.queue(new ProviderOutcome.Timeout<>());
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        assertNotNull(preview.stableId());
        assertEquals(2, routes.callCount());

        routes.reset();
        routes.queue(new ProviderOutcome.RateLimited<>());
        PrivateCarRoute retried = planning.previewRoute(trip.id());
        assertNotNull(retried.stableId());
        assertEquals(2, routes.callCount());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
    }

    @Test
    void nonRetryableProviderOutcomeIsNeverRetried() {
        Trip trip = readyTrip();
        routes.queue(new ProviderOutcome.Empty<>());
        assertThrows(ResponseStatusException.class, () -> planning.previewRoute(trip.id()));
        assertEquals(1, routes.callCount());

        routes.reset();
        routes.queue(new ProviderOutcome.Malformed<>("missing distance"));
        assertThrows(ResponseStatusException.class, () -> planning.previewRoute(trip.id()));
        assertEquals(1, routes.callCount());

        geocoding.reset();
        geocoding.queue(new ProviderOutcome.Empty<>());
        assertThrows(ResponseStatusException.class, () -> planning.previewRoute(trip.id()));
        assertEquals(1, geocoding.callCount());
    }

    @Test
    void confirmationRequiresLeadAndRejectsFuturePreviewTimestamp() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        assertThrows(ResponseStatusException.class,
                () -> planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), null,
                        "confirm-null-lead", "confirm-null-lead-key"));
        assertThrows(ResponseStatusException.class,
                () -> planning.confirmRoute(trip.id(), preview.stableId(), OffsetDateTime.now(clock).plusMinutes(5), 30,
                        "confirm-future", "confirm-future-key"));
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from reminders where trip_id=?", trip.id()));
    }

    @Test
    void confirmationPersistsExactlyOneRouteAndReminderInOneTransaction() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        PrivateCarPlanningService.ConfirmationResult result = confirm(trip, preview.stableId(), preview.fetchedAt(), "confirm-tx-key");
        Trip confirmed = result.trip();
        assertEquals(TripStatus.CONFIRMED, confirmed.status());
        assertEquals(1, count("select count(*) from trips where id=? and status='CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=? and stable_id=?", trip.id(), preview.stableId()));
        assertEquals(1, count("select count(*) from notification_policies where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from reminders where trip_id=? and status='" + ReminderStatus.SCHEDULE_PENDING.name() + "'", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='PRIVATE_CAR_ROUTE_CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", trip.id()));
        assertEquals(30, db.queryForObject("select reminder_lead_minutes from private_car_routes where trip_id=?", Integer.class, trip.id()));
        assertEquals(8_500, db.queryForObject("select toll_amount from private_car_routes where trip_id=?", Integer.class, trip.id()));
    }

    @Test
    void reminderDueUsesRecommendedDepartureMinusLead() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        confirm(trip, preview.stableId(), preview.fetchedAt(), "confirm-due-key");
        OffsetDateTime due = db.queryForObject("select due_at from trip_outbox where trip_id=? and operation='UPSERT'", OffsetDateTime.class, trip.id());
        // recommended departure 08:30 minus 30 minutes lead
        assertEquals(OffsetDateTime.parse("2030-01-01T08:00:00+09:00"), due);
        OffsetDateTime recommended = db.queryForObject("select recommended_departure_at from private_car_routes where trip_id=?", OffsetDateTime.class, trip.id());
        assertEquals(OffsetDateTime.parse("2030-01-01T08:30:00+09:00"), recommended);
    }

    @Test
    void expiredPreviewIsRejectedAndPersistsNothing() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        OffsetDateTime staleFetched = preview.fetchedAt().minusMinutes(11);
        var expired = assertThrows(ResponseStatusException.class,
                () -> confirm(trip, preview.stableId(), staleFetched, "confirm-expired-key"));
        assertEquals(400, expired.getStatusCode().value());
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from reminders where trip_id=?", trip.id()));
    }

    @Test
    void mismatchedProposalIsRejectedAndPersistsNothing() {
        Trip trip = readyTrip();
        planning.previewRoute(trip.id());
        var mismatch = assertThrows(ResponseStatusException.class,
                () -> confirm(trip, "0000000000000000000000000000000000000000000000000000000000000000",
                        OffsetDateTime.now(clock), "confirm-mismatch-key"));
        assertEquals(409, mismatch.getStatusCode().value());
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
    }

    @Test
    void nonDraftOrUnownedTripIsRejected() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 30, "confirm-state-1", "confirm-state-key-1");
        var already = assertThrows(ResponseStatusException.class,
                () -> planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 30, "confirm-state-2", "confirm-state-key-2"));
        assertEquals(409, already.getStatusCode().value());
        UUID other = UUID.randomUUID();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                other, "other-owner", "Seoul", "Busan", PrivateCarFixtures.DEPART, null, TripStatus.DRAFT.name(),
                OffsetDateTime.now(clock), OffsetDateTime.now(clock));
        assertThrows(ResponseStatusException.class,
                () -> planning.previewRoute(other));
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
    }

    @Test
    void injectedFailureRollsBackEverything() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        db.update("insert into trip_outbox(id,trip_id,operation,expected_version,scheduler_version,due_at,payload,created_at) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), trip.id(), "UPSERT", 2, 3, OffsetDateTime.now(clock), "{}", OffsetDateTime.now(clock));
        assertThrows(ResponseStatusException.class,
                () -> confirm(trip, preview.stableId(), preview.fetchedAt(), "confirm-rollback-key"));
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
        assertEquals(0, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from notification_policies where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_outbox where trip_id=?", trip.id()));
    }

    @Test
    void exactOnceIdempotencyReturnsSameResultWithoutDuplicates() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        String key = "same-confirm-key";
        PrivateCarPlanningService.ConfirmationResult first = planning.confirmRoute(trip.id(), preview.stableId(),
                preview.fetchedAt(), 30, "confirm-same", key);
        PrivateCarPlanningService.ConfirmationResult replay = planning.confirmRoute(trip.id(), preview.stableId(),
                preview.fetchedAt(), 30, "confirm-same", key);
        assertEquals(first.trip().id(), replay.trip().id());
        assertEquals(first.trip().version(), replay.trip().version());
        assertEquals(first.route().stableId(), replay.route().stableId());
        assertEquals(1, count("select count(*) from trips where id=?", trip.id()));
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='PRIVATE_CAR_ROUTE_CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from notification_policies where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_outbox where trip_id=?", trip.id()));
    }

    @Test
    void idempotentReplayStillReturnsStoredSuccessAfterPreviewTtlExpires() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        String key = "replay-after-ttl-key";
        PrivateCarPlanningService.ConfirmationResult first = planning.confirmRoute(trip.id(), preview.stableId(),
                preview.fetchedAt(), 30, "confirm-replay", key);
        clock.advance(PrivateCarPlanningService.PREVIEW_TTL.plusMinutes(1));

        PrivateCarPlanningService.ConfirmationResult replay = planning.confirmRoute(trip.id(), preview.stableId(),
                preview.fetchedAt(), 30, "confirm-replay", key);
        assertEquals(first.trip().id(), replay.trip().id());
        assertEquals(first.trip().version(), replay.trip().version());
        assertEquals(first.route().stableId(), replay.route().stableId());
        assertEquals(first.reminderLeadMinutes(), replay.reminderLeadMinutes());
        assertEquals(1, count("select count(*) from trips where id=?", trip.id()));
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='PRIVATE_CAR_ROUTE_CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from notification_policies where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", trip.id()));
        assertEquals(1, count("select count(*) from events"));
    }

    @Test
    void expiredPreviewWithFreshKeyIsRejectedAndPersistsNothing() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        String key = "replay-after-ttl-key";
        planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 30, "confirm-replay", key);
        clock.advance(PrivateCarPlanningService.PREVIEW_TTL.plusMinutes(1));

        var expired = assertThrows(ResponseStatusException.class,
                () -> planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 30,
                        "confirm-expired-2", "expired-fresh-key"));
        assertEquals(400, expired.getStatusCode().value());
        assertEquals(TripStatus.CONFIRMED, trips.find(trip.id()).status());
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(1, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", trip.id()));
        assertEquals(1, count("select count(*) from events"));
    }

    @Test
    void malformedProposalIdIsRejectedBeforeAnyProviderOrBusinessWrite() {
        Trip trip = readyTrip();
        String[] malformed = {
                null,
                "",
                "not-a-hex-id",
                "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                "0".repeat(64) + "0"
        };
        for (String proposalId : malformed) {
            var rejected = assertThrows(ResponseStatusException.class,
                    () -> planning.confirmRoute(trip.id(), proposalId, OffsetDateTime.now(clock), 30,
                            "confirm-malformed", "malformed-key-" + System.identityHashCode(proposalId)));
            assertEquals(400, rejected.getStatusCode().value());
        }
        assertEquals(0, geocoding.callCount());
        assertEquals(0, routes.callCount());
        assertEquals(TripStatus.DRAFT, trips.find(trip.id()).status());
        assertEquals(0, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from reminders where trip_id=?", trip.id()));
        assertEquals(0, count("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", trip.id()));
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        Trip trip = readyTrip();
        PrivateCarRoute preview = planning.previewRoute(trip.id());
        planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 30, "confirm-a", "conflict-key");
        var conflict = assertThrows(ResponseStatusException.class,
                () -> planning.confirmRoute(trip.id(), preview.stableId(), preview.fetchedAt(), 45, "confirm-b", "conflict-key"));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(1, count("select count(*) from private_car_routes where trip_id=?", trip.id()));
    }

    @Test
    void stableIdDiffersWhenRouteContentDiffers() {
        Trip trip = readyTrip();
        PrivateCarRoute first = planning.previewRoute(trip.id());
        // a different toll produces a different stable id and a different preview
        PrivateCarRoute second = PrivateCarRoute.create("Seoul", "Busan", PrivateCarFixtures.DEPART,
                FakeGeocodingPort.SEOUL, FakeGeocodingPort.BUSAN, FakeRouteProviderPort.DISTANCE_METERS,
                FakeRouteProviderPort.BASE_DURATION, FakeRouteProviderPort.TRAFFIC_DURATION, 9_000,
                "fake-route", "fake", OffsetDateTime.now(), PrivateCarRoute.DEFAULT_TTL);
        assertNotEquals(first.stableId(), second.stableId());
    }
}
