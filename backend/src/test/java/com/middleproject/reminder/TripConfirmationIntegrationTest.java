package com.middleproject.reminder;

import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-confirm;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class TripConfirmationIntegrationTest {

    @Autowired TripService trips;
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

    private Trip draft() {
        return trips.createDraft("Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), OffsetDateTime.parse("2030-01-03T18:00:00+09:00"),
                "draft-key-" + UUID.randomUUID());
    }

    private Map<String, Integer> eventCounts(UUID tripId) {
        return Map.of(
                "DRAFT_CREATED", db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", Integer.class, tripId),
                "AWAITING_CONFIRMATION", db.queryForObject("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", Integer.class, tripId),
                "CONFIRMED", db.queryForObject("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", Integer.class, tripId),
                "CANCELLED", db.queryForObject("select count(*) from trip_events where trip_id=? and type='CANCELLED'", Integer.class, tripId));
    }

    @Test
    void confirmationPersistsTripEventPolicyReminderAndOutboxInOneTransaction() {
        Trip draft = draft();
        Trip confirmed = trips.confirm(draft.id(), "confirm-1", "confirm-key");
        assertEquals(TripStatus.CONFIRMED, confirmed.status());
        assertEquals(1, db.queryForObject("select count(*) from trips where id=? and status='CONFIRMED'", Integer.class, draft.id()));
        Map<String, Integer> events = eventCounts(draft.id());
        assertEquals(1, events.get("DRAFT_CREATED"));
        assertEquals(1, events.get("AWAITING_CONFIRMATION"));
        assertEquals(1, events.get("CONFIRMED"));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies where trip_id=?", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=? and status='" + ReminderStatus.SCHEDULE_PENDING.name() + "'", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=? and operation='UPSERT'", Integer.class, draft.id()));
    }

    @Test
    void failedConfirmationLeavesNoPartialState() {
        Trip draft = draft();
        // insert a deterministic outbox collision for the scheduler_version the confirmation would write
        db.update("insert into trip_outbox(id,trip_id,operation,expected_version,scheduler_version,due_at,payload,created_at) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), draft.id(), "UPSERT", 1, 2, OffsetDateTime.now(), "{}", OffsetDateTime.now());
        assertThrows(ResponseStatusException.class, () -> trips.confirm(draft.id(), "confirm-bad", "confirm-bad-key"));
        assertEquals(TripStatus.DRAFT, trips.find(draft.id()).status());
        Map<String, Integer> events = eventCounts(draft.id());
        assertEquals(1, events.get("DRAFT_CREATED"));
        assertEquals(0, events.get("AWAITING_CONFIRMATION"));
        assertEquals(0, events.get("CONFIRMED"));
        assertEquals(0, db.queryForObject("select count(*) from notification_policies where trip_id=?", Integer.class, draft.id()));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, draft.id()));
    }

    @Test
    void failedConfirmationRollsBackToPreviousStateAfterDeterministicCollision() {
        Trip draft = draft();
        trips.answerQuestion(draft.id(), "Q1", "A1", "answer-key-collide");
        // after one answer the trip is at version 1, so confirmation would write scheduler_version 3
        db.update("insert into trip_outbox(id,trip_id,operation,expected_version,scheduler_version,due_at,payload,created_at) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), draft.id(), "UPSERT", 2, 3, OffsetDateTime.now(), "{}", OffsetDateTime.now());
        assertThrows(ResponseStatusException.class, () -> trips.confirm(draft.id(), "confirm-collide", "confirm-collide-key"));
        assertEquals(TripStatus.DRAFT, trips.find(draft.id()).status());
        assertTrue(trips.find(draft.id()).draftContext().containsKey("Q1"));
        Map<String, Integer> events = eventCounts(draft.id());
        assertEquals(1, events.get("DRAFT_CREATED"));
        assertEquals(0, events.get("AWAITING_CONFIRMATION"));
        assertEquals(0, events.get("CONFIRMED"));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_ANSWERED'", Integer.class, draft.id()));
        assertEquals(0, db.queryForObject("select count(*) from notification_policies where trip_id=?", Integer.class, draft.id()));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, draft.id()));
    }

    @Test
    void sameIdempotencyKeyDoesNotDuplicateTripOrReminder() {
        Trip draft = draft();
        Trip first = trips.confirm(draft.id(), "confirm-same", "same-confirm-key");
        Trip replay = trips.confirm(draft.id(), "confirm-same", "same-confirm-key");
        assertEquals(first.id(), replay.id());
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, draft.id()));
        Map<String, Integer> events = eventCounts(draft.id());
        assertEquals(1, events.get("DRAFT_CREATED"));
        assertEquals(1, events.get("AWAITING_CONFIRMATION"));
        assertEquals(1, events.get("CONFIRMED"));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies where trip_id=?", Integer.class, draft.id()));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, draft.id()));
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        Trip first = trips.createDraft("Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), OffsetDateTime.parse("2030-01-03T18:00:00+09:00"),
                "shared-draft-key");
        trips.confirm(first.id(), "confirm-a", "shared-confirm-key");
        var conflict = assertThrows(ResponseStatusException.class,
                () -> trips.confirm(first.id(), "confirm-b", "shared-confirm-key"));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(1, db.queryForObject("select count(*) from trips", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from reminders", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from trip_events", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox", Integer.class));
    }

    @Test
    void draftKeyConflictRejectsSecondDraftWithoutCreatingRows() {
        trips.createDraft("Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), OffsetDateTime.parse("2030-01-03T18:00:00+09:00"),
                "same-draft-key");
        var conflict = assertThrows(ResponseStatusException.class,
                () -> trips.createDraft("Busan", "Jeju",
                        OffsetDateTime.parse("2030-02-01T10:00:00+09:00"), OffsetDateTime.parse("2030-02-02T18:00:00+09:00"),
                        "same-draft-key"));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(1, db.queryForObject("select count(*) from trips", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from reminders", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from notification_policies", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from trip_events", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from trip_outbox", Integer.class));
    }

    @Test
    void optimisticLockConflictOnStaleVersionIsRejected() {
        Trip draft = draft();
        trips.confirm(draft.id(), "confirm-1", "confirm-key-1");
        Trip stale = trips.find(draft.id());
        assertEquals(TripStatus.CONFIRMED, stale.status());
        assertThrows(ResponseStatusException.class, () -> trips.cancel(stale.id(), stale.version() - 1, "cancel-stale"));
        assertEquals(TripStatus.CONFIRMED, trips.find(draft.id()).status());
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, draft.id()));
    }

    @Test
    void draftContextAccumulatesQuestionsAndAnswers() {
        Trip draft = draft();
        Trip withAnswer = trips.answerQuestion(draft.id(), "Q1", "A1", "answer-key-1");
        assertEquals("A1", withAnswer.draftContext().get("Q1"));
        Trip withMore = trips.answerQuestion(draft.id(), "Q2", "A2", "answer-key-2");
        assertEquals("A2", withMore.draftContext().get("Q2"));
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, draft.id()));
    }

    @Test
    void demoOwnerIsUsedAndClientProvidedOwnerIsIgnored() {
        Trip draft = trips.createDraft("Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), OffsetDateTime.parse("2030-01-03T18:00:00+09:00"),
                "owner-draft-key", "spoofed-user");
        assertEquals("demo-owner", draft.ownerId());
        assertEquals(0, db.queryForObject("select count(*) from trips where owner_id='spoofed-user'", Integer.class));
    }

    @Test
    void listAndGetAreScopedToTheConfiguredDemoOwner() {
        Trip mine = draft();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                UUID.randomUUID(), "other-owner", "Seoul", "Busan",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, TripStatus.DRAFT.name(),
                OffsetDateTime.now(), OffsetDateTime.now());
        List<Trip> all = trips.all();
        assertTrue(all.stream().allMatch(t -> t.ownerId().equals("demo-owner")));
        assertEquals(1, all.size());
        UUID other = db.queryForObject("select id from trips where owner_id='other-owner'", UUID.class);
        assertThrows(ResponseStatusException.class, () -> trips.find(other));
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, mine.id()));
    }

    @Test
    void sharedValidationRejectsBlankLocationsAndReversedTimes() {
        assertThrows(ResponseStatusException.class, () -> trips.createDraft(" ", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, "blank-departure-key"));
        assertThrows(ResponseStatusException.class, () -> trips.createDraft("Seoul", "",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, "blank-destination-key"));
        assertThrows(ResponseStatusException.class, () -> trips.createDraft("Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), OffsetDateTime.parse("2030-01-01T09:00:00+09:00"),
                "reversed-times-key"));
        assertEquals(0, db.queryForObject("select count(*) from trips", Integer.class));
    }
}
