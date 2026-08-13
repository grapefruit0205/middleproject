package com.middleproject.reminder;

import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.port.SchedulerPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {ReminderPlatformApplication.class, Postgres16IntegrationTest.Configuration.class})
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_PASSWORD", matches = ".+")
class Postgres16IntegrationTest {
    @Autowired EventService events;
    @Autowired PolicyService policies;
    @Autowired ReminderService reminders;
    @Autowired SchedulerOutboxService outbox;
    @Autowired ReminderDeliveryService delivery;
    @Autowired JdbcTemplate db;
    @Autowired RecordingScheduler scheduler;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("POSTGRES_TEST_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void verifyPostgres16AndClean() {
        Assumptions.assumeTrue(db.queryForObject("show server_version_num", String.class).startsWith("16"), "PostgreSQL 16 is required");
        cleanRows();
        scheduler.reset();
    }

    @AfterEach
    void cleanAfter() { cleanRows(); }

    @Test
    void productionClaimAndReceiptConflictArePostgres16Compatible() {
        var event = events.create("postgres-test", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "pg-event-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 10, "pg-policy-" + UUID.randomUUID());
        var reminder = reminders.create(event.id(), policy.id(), "pg-reminder-" + UUID.randomUUID());

        assertEquals(1, outbox.reconcile(10));
        assertEquals(1, scheduler.calls.get());
        String body = "{\"reminderId\":\"" + reminder.id() + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminder.id() + ":1\"}";
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(body));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(body));
    }

    @Test
    void concurrentWorkersSkipLockedClaimsAndPreserveSameReminderOrdering() throws Exception {
        var first = newReminder("first");
        var independent = newReminder("independent");
        db.update("delete from schedule_outbox where reminder_id in (?, ?)", first.id(), independent.id());
        OffsetDateTime created = OffsetDateTime.now().minusMinutes(5);
        insertOutbox(first.id(), 1, created, "first");
        insertOutbox(first.id(), 2, created.plusSeconds(1), "first-newer");
        insertOutbox(independent.id(), 1, created.plusSeconds(2), "independent");

        scheduler.blockFirst = true;
        SchedulerOutboxService workerTwo = new SchedulerOutboxService(db, scheduler,
                new TransactionTemplate(transactionManager));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstWorker = executor.submit(() -> outbox.reconcile(1));
            assertTrue(scheduler.firstCallEntered.await(5, TimeUnit.SECONDS));

            Future<Integer> secondWorker = executor.submit(() -> workerTwo.reconcile(1));
            assertEquals(1, secondWorker.get(5, TimeUnit.SECONDS));
            assertEquals("CLAIMED", statusAt(first.id(), 1));
            assertEquals("PENDING", statusAt(first.id(), 2));
            assertEquals("SUCCEEDED", statusAt(independent.id(), 1));
            assertEquals(1, scheduler.independentCalls.get());

            scheduler.releaseFirst.countDown();
            assertEquals(1, firstWorker.get(5, TimeUnit.SECONDS));
            assertEquals("SUCCEEDED", statusAt(first.id(), 1));

            assertEquals(1, workerTwo.reconcile(1));
            assertEquals("SUCCEEDED", statusAt(first.id(), 2));
        } finally {
            scheduler.releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            scheduler.blockFirst = false;
        }
    }

    private com.middleproject.reminder.domain.Reminder newReminder(String key) {
        var event = events.create("meeting", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "event-" + key + "-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 10, "policy-" + key + "-" + UUID.randomUUID());
        return reminders.create(event.id(), policy.id(), key + "-" + UUID.randomUUID());
    }

    private void insertOutbox(UUID reminderId, long version, OffsetDateTime createdAt, String key) {
        db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload,available_at,created_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), reminderId, "UPSERT", version - 1, version, OffsetDateTime.now().plusSeconds(version),
                "{\"reminderId\":\"" + reminderId + "\",\"schedulerVersion\":" + version + ",\"idempotencyKey\":\"" + key + "\"}", createdAt, createdAt);
    }

    private String statusAt(UUID reminderId, long version) {
        return db.queryForObject("select status from schedule_outbox where reminder_id=? and scheduler_version=?", String.class, reminderId, version);
    }

    private void cleanRows() {
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @TestConfiguration
    static class Configuration {
        @Bean RecordingScheduler recordingScheduler() { return new RecordingScheduler(); }
        @Bean @Primary SchedulerPort schedulerPort(RecordingScheduler scheduler) { return scheduler; }
    }

    static class RecordingScheduler implements SchedulerPort {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger independentCalls = new AtomicInteger();
        volatile boolean blockFirst;
        volatile CountDownLatch firstCallEntered = new CountDownLatch(1);
        volatile CountDownLatch releaseFirst = new CountDownLatch(1);

        void reset() {
            calls.set(0); independentCalls.set(0); blockFirst = false;
            firstCallEntered = new CountDownLatch(1); releaseFirst = new CountDownLatch(1);
        }

        public void register(UUID id, long version, OffsetDateTime dueAt, String payload) {
            calls.incrementAndGet();
            if (payload.contains("\"idempotencyKey\":\"first\"") && blockFirst) {
                firstCallEntered.countDown();
                try { releaseFirst.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            if (payload.contains("independent")) independentCalls.incrementAndGet();
        }
        public void cancel(UUID id, long version) { calls.incrementAndGet(); }
    }
}
