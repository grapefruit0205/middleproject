package com.middleproject.reminder;

import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.SchedulerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {ReminderPlatformApplication.class, SchedulerOutboxIntegrationTest.Configuration.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduler-outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class SchedulerOutboxIntegrationTest {
    @Autowired EventService events;
    @Autowired PolicyService policies;
    @Autowired ReminderService reminders;
    @Autowired SchedulerOutboxService outbox;
    @Autowired ReminderDeliveryService delivery;
    @Autowired JdbcTemplate db;
    @Autowired FakeScheduler scheduler;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach void isolateData() {
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        scheduler.reset();
    }

    @Test void commitThenSchedulerFailureIsRecoveredWithoutRetryTiming() {
        var reminder = newReminder("recovery");
        makeAvailable(reminder.id());
        scheduler.failNext.set(true);

        outbox.reconcile(10);
        assertEquals("RETRY", outboxStatus(reminder.id(), "UPSERT"));
        assertEquals(ReminderStatus.CREATED, reminders.find(reminder.id()).status());

        makeAvailable(reminder.id());
        outbox.reconcile(10);
        assertEquals("SUCCEEDED", outboxStatus(reminder.id(), "UPSERT"));
        assertEquals(ReminderStatus.SCHEDULED, reminders.find(reminder.id()).status());
        assertEquals(1L, reminders.find(reminder.id()).version());
        assertEquals(1, scheduler.successfulRegistrations.get());
    }

    @Test void duplicateDeliveryIsIdempotent() {
        var reminder = schedule(newReminder("duplicate"));
        String body = payload(reminder.id(), 1);

        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(body));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(body));
        assertEquals(ReminderStatus.DISPATCHED, reminders.find(reminder.id()).status());
        assertEquals(1, db.queryForObject("select count(*) from reminder_delivery_receipt", Integer.class));
    }

    @Test void cancelledDeliveryIsIgnored() {
        var reminder = schedule(newReminder("cancelled"));
        long version = reminders.find(reminder.id()).version();
        reminders.transition(reminder.id(), ReminderStatus.CANCELLED, version, "cancel-key");
        makeAvailable(reminder.id());
        outbox.reconcile(10);

        assertEquals("SUCCEEDED", outboxStatus(reminder.id(), "DELETE"));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(payload(reminder.id(), 1)));
        assertEquals(ReminderStatus.CANCELLED, reminders.find(reminder.id()).status());
    }

    @Test void staleSchedulerVersionUsesRealH2SchemaAndUpdatePath() {
        var reminder = schedule(newReminder("stale-version"));
        assertEquals(1L, reminders.find(reminder.id()).version());

        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(payload(reminder.id(), 1)));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(payload(reminder.id(), 0)));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(payload(reminder.id(), 99)));
        assertEquals(ReminderStatus.DISPATCHED, reminders.find(reminder.id()).status());
    }

    @Test void reorderedSameReminderRowsBlockNewerUntilOlderSucceeds() throws Exception {
        var reminder = newReminder("reordered");
        db.update("delete from schedule_outbox where reminder_id=?", reminder.id());
        OffsetDateTime old = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime newer = old.plusSeconds(1);
        insertOutbox(reminder.id(), "UPSERT", 0, 1, old, "old-key");
        insertOutbox(reminder.id(), "UPSERT", 1, 2, newer, "new-key");

        scheduler.blockOlder.set(true);
        SchedulerOutboxService workerTwo = new SchedulerOutboxService(db, scheduler, new TransactionTemplate(transactionManager));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> outbox.reconcile(10));
            assertTrue(scheduler.olderCallEntered.await(5, TimeUnit.SECONDS));

            Future<Integer> second = executor.submit(() -> workerTwo.reconcile(10));
            assertEquals(0, second.get(5, TimeUnit.SECONDS));
            assertEquals(0, scheduler.newerInvocations.get());
            assertEquals("CLAIMED", statusAt(1));
            assertEquals("PENDING", statusAt(2));

            scheduler.releaseOlder.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
            assertEquals("SUCCEEDED", statusAt(1));
            assertEquals(1, scheduler.successfulRegistrations.get());

            assertEquals(1, workerTwo.reconcile(10));
            assertEquals("SUCCEEDED", statusAt(2));
            assertEquals(2, scheduler.successfulRegistrations.get());
            assertEquals(1, scheduler.newerInvocations.get());
        } finally {
            scheduler.releaseOlder.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            scheduler.blockOlder.set(false);
        }
    }

    private com.middleproject.reminder.domain.Reminder newReminder(String key) {
        var event = events.create("meeting", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "event-" + key);
        var policy = policies.create("EMAIL", 10, "policy-" + key);
        return reminders.create(event.id(), policy.id(), key);
    }

    private com.middleproject.reminder.domain.Reminder schedule(com.middleproject.reminder.domain.Reminder reminder) {
        makeAvailable(reminder.id());
        outbox.reconcile(10);
        return reminders.find(reminder.id());
    }

    private void makeAvailable(UUID id) {
        db.update("update schedule_outbox set available_at=? where reminder_id=?", OffsetDateTime.now().minusMinutes(1), id);
    }

    private String outboxStatus(UUID id, String operation) {
        return db.queryForObject("select status from schedule_outbox where reminder_id=? and operation=?", String.class, id, operation);
    }

    private String statusAt(long schedulerVersion) {
        return db.queryForObject("select status from schedule_outbox where scheduler_version=?", String.class, schedulerVersion);
    }

    private void insertOutbox(UUID reminderId, String operation, long expected, long schedulerVersion, OffsetDateTime createdAt, String key) {
        db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload,available_at,created_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), reminderId, operation, expected, schedulerVersion, OffsetDateTime.now().plusSeconds(schedulerVersion), payload(reminderId, schedulerVersion, key), createdAt, createdAt);
    }

    private String payload(UUID id, long version) { return payload(id, version, id + ":" + version); }
    private String payload(UUID id, long version, String key) { return "{\"reminderId\":\"" + id + "\",\"schedulerVersion\":" + version + ",\"idempotencyKey\":\"" + key + "\"}"; }

    @TestConfiguration
    static class Configuration {
        @Bean FakeScheduler fakeScheduler() { return new FakeScheduler(); }
        @Bean @Primary SchedulerPort schedulerPort(FakeScheduler scheduler) { return scheduler; }
    }

    static class FakeScheduler implements SchedulerPort {
        final AtomicBoolean failNext = new AtomicBoolean();
        final AtomicBoolean blockOlder = new AtomicBoolean();
        final AtomicInteger successfulRegistrations = new AtomicInteger();
        final AtomicInteger newerInvocations = new AtomicInteger();
        volatile CountDownLatch olderCallEntered = new CountDownLatch(1);
        volatile CountDownLatch releaseOlder = new CountDownLatch(1);
        void reset() {
            failNext.set(false); blockOlder.set(false); successfulRegistrations.set(0); newerInvocations.set(0);
            olderCallEntered = new CountDownLatch(1); releaseOlder = new CountDownLatch(1);
        }
        public void register(UUID id, long version, OffsetDateTime dueAt, String payload) {
            if (failNext.compareAndSet(true, false)) throw new IllegalStateException("temporary scheduler failure");
            if (payload.contains("old-key") && blockOlder.compareAndSet(true, false)) {
                olderCallEntered.countDown();
                try { releaseOlder.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            if (payload.contains("new-key")) newerInvocations.incrementAndGet();
            successfulRegistrations.incrementAndGet();
        }
        public void cancel(UUID id, long version) { }
    }
}
