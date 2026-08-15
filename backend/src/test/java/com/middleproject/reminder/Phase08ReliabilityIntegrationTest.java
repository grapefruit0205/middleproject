package com.middleproject.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.IdempotencyService;
import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.NotificationSender;
import com.middleproject.reminder.port.NotificationTimeoutException;
import com.middleproject.reminder.infrastructure.persistence.JdbcIdempotencyAdapter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {ReminderPlatformApplication.class, SchedulerOutboxIntegrationTest.Configuration.class, Phase08ReliabilityIntegrationTest.Configuration.class, Phase08ReliabilityIntegrationTest.TransactionalFailureConfiguration.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:phase08-matrix;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class Phase08ReliabilityIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired EventService events;
    @Autowired PolicyService policies;
    @Autowired ReminderService reminders;
    @Autowired IdempotencyService idempotency;
    @Autowired SchedulerOutboxService outbox;
    @Autowired ReminderDeliveryService delivery;
    @Autowired ObjectMapper mapper;
    @Autowired SchedulerOutboxIntegrationTest.FakeScheduler scheduler;
    @Autowired JdbcIdempotencyAdapter idempotencyAdapter;
    @Autowired Clock clock;

    @Test void liveLeaseCannotBeReclaimedButExpiredSameHashCanBeClaimed() {
        String scope = "lease", key = "lease-key", hash = "hash-a";
        assertNotNull(idempotencyAdapter.reserve(scope, key, hash));
        assertNull(idempotencyAdapter.claimExpired(scope, key, hash));
        db.update("update idempotency_record set lease_until=? where scope=? and idempotency_key=?", OffsetDateTime.now(clock).minusSeconds(1), scope, key);
        assertNotNull(idempotencyAdapter.claimExpired(scope, key, hash));
        assertEquals(hash, idempotencyAdapter.find(scope, key).orElseThrow().requestHash());
        assertNull(idempotencyAdapter.claimExpired(scope, key, hash));
    }

    @Test void staleClaimCannotCompleteAfterNewerClaim() {
        String scope = "fence", key = "fence-key", hash = "hash-fence";
        String first = idempotencyAdapter.reserve(scope, key, hash);
        db.update("update idempotency_record set lease_until=? where scope=? and idempotency_key=?", OffsetDateTime.now(clock).minusSeconds(1), scope, key);
        String second = idempotencyAdapter.claimExpired(scope, key, hash);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
        assertFalse(idempotencyAdapter.complete(scope, key, first, 200, "stale"));
        assertTrue(idempotencyAdapter.complete(scope, key, second, 200, "current"));
        assertEquals("current", idempotencyAdapter.find(scope, key).orElseThrow().responseBody());
    }

    @Test void outerTransactionCommitFailureLeavesReservationRetryable() {
        String key = "commit-failure-key";
        OffsetDateTime starts = OffsetDateTime.parse("2030-01-01T10:00:00Z");
        assertThrows(IllegalStateException.class, () -> transactionalFailureService.failAtCommit(key, starts));
        assertEquals(0, db.queryForObject("select count(*) from events", Integer.class));
        assertEquals("IN_PROGRESS", db.queryForObject("select status from idempotency_record where idempotency_key=?", String.class, key));
        assertNull(db.queryForObject("select response_body from idempotency_record where idempotency_key=?", String.class, key));
        mutableClock.advance(Duration.ofSeconds(31));
        assertEquals("commit-failure", events.create("commit-failure", starts, null, key).title());
        assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
        assertEquals("COMPLETED", db.queryForObject("select status from idempotency_record where idempotency_key=?", String.class, key));
    }

    @Test void staleClaimantIsFencedAndCannotFailNewCompletion() throws Exception {
        String key = "stale-claim-key";
        StaleClaimService service = staleClaimService;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> {
                try { service.workerA(key); return null; } catch (Throwable failure) { return failure; }
            });
            assertTrue(service.awaitWorkerAEntered());
            mutableClock.advance(Duration.ofSeconds(31));
            Future<Throwable> second = pool.submit(() -> {
                try { service.workerB(key); return null; } catch (Throwable failure) { return failure; }
            });
            assertNull(second.get());
            service.releaseWorkerA().countDown();
            Throwable failure = first.get();
            assertInstanceOf(IllegalStateException.class, failure);
            assertTrue(failure.getMessage().contains("fenced"));
            assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
            assertEquals("worker-b", db.queryForObject("select title from events", String.class));
            assertEquals("COMPLETED", db.queryForObject("select status from idempotency_record where idempotency_key=?", String.class, key));
            assertEquals("worker-b", db.queryForObject("select response_body from idempotency_record where idempotency_key=?", String.class, key).replace("\"", ""));
        } finally {
            if (service != null) service.releaseWorkerA().countDown();
            pool.shutdownNow();
        }
    }

    @Test void outerTransactionRollbackLeavesFailedIdempotencyRecordRetryable() {
        String key = "rollback-key";
        OffsetDateTime starts = OffsetDateTime.parse("2030-01-01T10:00:00Z");
        assertThrows(IllegalStateException.class, () -> transactionalFailureService.fail(key, starts));
        assertEquals(0, db.queryForObject("select count(*) from events", Integer.class));
        assertEquals("FAILED", db.queryForObject("select status from idempotency_record where idempotency_key=?", String.class, key));
        assertEquals("rolled back", events.create("rolled back", starts, null, key).title());
    }

    @Autowired TransactionalFailureService transactionalFailureService;
    @Autowired StaleClaimService staleClaimService;
    @Autowired MutableClock mutableClock;

    @TestConfiguration
    static class Configuration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    @TestConfiguration
    static class TransactionalFailureConfiguration {
        @Bean TransactionalFailureService transactionalFailureService(IdempotencyService idempotency, JdbcTemplate db) { return new TransactionalFailureService(idempotency, db); }
        @Bean StaleClaimService staleClaimService(IdempotencyService idempotency, JdbcTemplate db) { return new StaleClaimService(idempotency, db); }
    }
    static class TransactionalFailureService {
        private final IdempotencyService idempotency; private final JdbcTemplate db;
        TransactionalFailureService(IdempotencyService idempotency, JdbcTemplate db) { this.idempotency = idempotency; this.db = db; }
        @Transactional public void fail(String key, OffsetDateTime starts) {
            idempotency.execute("events:create", key, new Object[]{"rolled back", starts, null}, String.class, () -> {
                db.update("insert into events(id,title,starts_at,version,created_at,updated_at) values(?,?,?,0,?,?)", UUID.randomUUID(), "rolled back", starts, starts, starts);
                throw new IllegalStateException("deliberate rollback");
            });
        }
        @Transactional public void failAtCommit(String key, OffsetDateTime starts) {
            idempotency.execute("events:create", key, new Object[]{"commit-failure", starts, null}, String.class, () -> {
                db.update("insert into events(id,title,starts_at,version,created_at,updated_at) values(?,?,?,0,?,?)", UUID.randomUUID(), "commit-failure", starts, starts, starts);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    public void beforeCommit(boolean readOnly) { throw new IllegalStateException("deterministic commit failure"); }
                });
                return "commit-failure";
            });
        }
    }
    static class StaleClaimService {
        private final IdempotencyService idempotency; private final JdbcTemplate db;
        CountDownLatch workerAEntered = new CountDownLatch(1);
        CountDownLatch releaseWorkerA = new CountDownLatch(1);
        StaleClaimService(IdempotencyService idempotency, JdbcTemplate db) { this.idempotency = idempotency; this.db = db; }
        boolean awaitWorkerAEntered() { try { return workerAEntered.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; } }
        CountDownLatch releaseWorkerA() { return releaseWorkerA; }
        @Transactional public String workerA(String key) {
            return idempotency.execute("events:stale", key, "same", String.class, () -> {
                insert("worker-a"); workerAEntered.countDown(); awaitRelease(); return "worker-a";
            });
        }
        @Transactional public String workerB(String key) {
            return idempotency.execute("events:stale", key, "same", String.class, () -> { insert("worker-b"); return "worker-b"; });
        }
        private void insert(String title) { db.update("insert into events(id,title,starts_at,version,created_at,updated_at) values(?,?,?,0,?,?)", UUID.randomUUID(), title, OffsetDateTime.parse("2030-01-01T10:00:00Z"), OffsetDateTime.now(), OffsetDateTime.now()); }
        private void awaitRelease() { try { releaseWorkerA.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());
        void advance(Duration duration) { current.updateAndGet(value -> value.plus(duration)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current.get(); }
        void reset() { current.set(Instant.now()); }
    }

    @BeforeEach void reset() {
        mutableClock.reset();
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from notification_attempt");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        scheduler.reset();
    }

    @Test void sequentialClientRetryReturnsIdenticalResponseAndOneRow() {
        var first = events.create("retry", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "phase08-event");
        var replay = events.create("retry", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "phase08-event");
        assertEquals(first, replay);
        assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
    }

    @Test void failedBusinessAttemptCanBeRetriedWithSameKey() {
        String key = "phase08-failed-then-retry";
        assertThrows(IllegalStateException.class, () -> idempotency.execute("phase08", key, "same-request", String.class,
                () -> { throw new IllegalStateException("temporary business failure"); }));
        String result = idempotency.execute("phase08", key, "same-request", String.class, () -> "committed-result");
        assertEquals("committed-result", result);
        assertEquals("COMPLETED", db.queryForObject("select status from idempotency_record where scope=? and idempotency_key=?", String.class, "phase08", key));
        assertEquals(1, db.queryForObject("select attempts from idempotency_record where scope=? and idempotency_key=?", Integer.class, "phase08", key));
    }

    @Test void exhaustedIdempotencyAttemptsReturnRetryLimitConflict() {
        String key = "phase08-exhausted";
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThrows(IllegalStateException.class, () -> idempotency.execute("phase08", key, "same-request", String.class,
                    () -> { throw new IllegalStateException("still failing"); }));
        }
        var conflict = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> idempotency.execute("phase08", key, "same-request", String.class, () -> "must-not-run"));
        assertEquals(409, conflict.getStatusCode().value());
        assertTrue(conflict.getReason().contains("retry limit"));
    }

    @Test void schedulerFailureAfterCommitIsReconciled() {
        var reminder = newReminder("scheduler-failure");
        makeAvailable(reminder.id());
        scheduler.failNext.set(true);
        outbox.reconcile(10);
        assertEquals("RETRY", outboxStatus(reminder.id(), "UPSERT"));
        makeAvailable(reminder.id());
        outbox.reconcile(10);
        assertEquals("SUCCEEDED", outboxStatus(reminder.id(), "UPSERT"));
        assertEquals(ReminderStatus.SCHEDULED, reminders.find(reminder.id()).status());
    }

    @Test void providerTimeoutThenLaterSuccessRecordsBothAttempts() {
        var reminder = newReminder("provider-timeout");
        reminders.transition(reminder.id(), ReminderStatus.SCHEDULE_PENDING, 0, "transition-timeout");
        db.update("update reminders set status='DISPATCHED' where id=?", reminder.id());
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.channel()).thenReturn(NotificationSender.Channel.EMAIL);
        when(sender.send(any())).thenThrow(new NotificationTimeoutException("provider timed out", null))
                .thenReturn(new NotificationSender.SendResult("provider-ok"));
        NotificationDeliveryService service = new NotificationDeliveryService(db, sender);

        var timeout = service.deliver(reminder.id(), "a@example.test", "subject", "body");
        var success = service.deliver(reminder.id(), "a@example.test", "subject", "body");
        assertEquals("RETRYABLE_TIMEOUT", timeout.status());
        assertEquals("SUCCEEDED", success.status());
        assertEquals(ReminderStatus.DELIVERED, reminders.find(reminder.id()).status());
        assertEquals(2, db.queryForObject("select count(*) from notification_attempt where reminder_id=?", Integer.class, reminder.id()));
    }

    @Test void concurrentDistinctKeysWithSameVersionYieldOneUpdateAndOneConflict() throws Exception {
        var reminder = newReminder("optimistic-concurrency");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> updateAfter(start, reminder.id(), "first-update", "one"));
            Future<Throwable> second = pool.submit(() -> updateAfter(start, reminder.id(), "second-update", "two"));
            start.countDown();
            Throwable firstFailure = first.get();
            Throwable secondFailure = second.get();
            assertTrue((firstFailure == null) ^ (secondFailure == null));
            Throwable conflict = firstFailure == null ? secondFailure : firstFailure;
            assertInstanceOf(org.springframework.web.server.ResponseStatusException.class, conflict);
            assertEquals(409, ((org.springframework.web.server.ResponseStatusException) conflict).getStatusCode().value());
            UUID eventId = db.queryForObject("select event_id from reminders where id=?", UUID.class, reminder.id());
            assertEquals(1L, events.find(eventId).version());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void duplicateDeliveryReceiptIsIgnoredAfterFirstAcceptance() {
        var reminder = newReminder("duplicate-receipt");
        makeAvailable(reminder.id());
        outbox.reconcile(10);
        assertEquals(ReminderStatus.SCHEDULED, reminders.find(reminder.id()).status());
        String body = "{\"reminderId\":\"" + reminder.id() + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminder.id() + ":1\"}";
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(body));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(body));
        assertEquals(1, db.queryForObject("select count(*) from reminder_delivery_receipt", Integer.class));
    }

    private Throwable updateAfter(CountDownLatch start, UUID id, String key, String title) {
        try {
            start.await();
            UUID eventId = db.queryForObject("select event_id from reminders where id=?", UUID.class, id);
            var event = events.find(eventId);
            events.update(eventId, title, event.startsAt(), event.endsAt(), 0, key);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private com.middleproject.reminder.domain.Reminder newReminder(String key) {
        var event = events.create("meeting-" + key, OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "event-" + key);
        var policy = policies.create("EMAIL", 10, "policy-" + key);
        return reminders.create(event.id(), policy.id(), "reminder-" + key);
    }

    private void makeAvailable(UUID id) { db.update("update schedule_outbox set available_at=? where reminder_id=?", OffsetDateTime.now().minusMinutes(1), id); }
    private String outboxStatus(UUID id, String operation) { return db.queryForObject("select status from schedule_outbox where reminder_id=? and operation=?", String.class, id, operation); }
}
