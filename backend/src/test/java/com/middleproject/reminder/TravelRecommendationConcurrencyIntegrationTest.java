package com.middleproject.reminder;

import com.middleproject.reminder.application.TravelRecommendationService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.TravelContextResult;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.support.PrivateCarFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Concurrency contract for consent proposal creation: two threads invoking the context
 * operation for the same owned trip in separate transactions must both succeed and leave
 * exactly one PROPOSED consent row. Uses the deterministic production provider adapters
 * (no mutable fakes), a bounded two-thread pool, and a shared start latch with a timeout
 * so there is no arbitrary sleeping or flaky timing.
 */
@SpringBootTest(classes = {ReminderPlatformApplication.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:travel-rec-concurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class TravelRecommendationConcurrencyIntegrationTest {

    @Autowired TravelRecommendationService recommendations;
    @Autowired TripService trips;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
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
    }

    @Test
    void concurrentContextCallsForSameOwnedTripBothSucceedWithExactlyOneProposedRow() throws Exception {
        UUID tripId = PrivateCarFixtures.readyDraft(trips);
        trips.confirm(tripId, "confirm-" + UUID.randomUUID(), "confirm-key-" + UUID.randomUUID());
        Trip trip = trips.find(tripId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TravelContextResult> first = pool.submit(() -> contextAfter(start, trip));
            Future<TravelContextResult> second = pool.submit(() -> contextAfter(start, trip));
            start.countDown();
            TravelContextResult firstResult = first.get(30, TimeUnit.SECONDS);
            TravelContextResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(ConsentStatus.PROPOSED, firstResult.consentStatus());
            assertEquals(ConsentStatus.PROPOSED, secondResult.consentStatus());
            assertEquals(1, db.queryForObject(
                    "select count(*) from travel_recommendation_consent where trip_id=?", Integer.class, trip.id()));
            assertEquals(ConsentStatus.PROPOSED, ConsentStatus.valueOf(db.queryForObject(
                    "select status from travel_recommendation_consent where trip_id=?", String.class, trip.id())));
        } finally {
            pool.shutdownNow();
        }
    }

    private TravelContextResult contextAfter(CountDownLatch start, Trip trip) {
        try {
            assertNotNull(start);
            if (!start.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent start latch timed out");
            }
            return recommendations.context(trip.id(), DepartureTiming.SAME_DAY, RecommendationSort.DISTANCE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent context call interrupted", e);
        }
    }
}
