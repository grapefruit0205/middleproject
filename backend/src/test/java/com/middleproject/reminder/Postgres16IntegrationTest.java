package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.IdempotencyService;
import com.middleproject.reminder.infrastructure.persistence.JdbcIdempotencyAdapter;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.port.SchedulerPort;
import com.middleproject.reminder.port.NotificationSender;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {ReminderPlatformApplication.class, Postgres16IntegrationTest.Configuration.class, Postgres16IntegrationTest.TransactionalFailureConfiguration.class})
@AutoConfigureMockMvc
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
    @Autowired NotificationDeliveryService notifications;
    @Autowired BlockingSender notificationSender;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcIdempotencyAdapter idempotencyAdapter;
    @Autowired MutableClock mutableClock;
    @Autowired TransactionalFailureService transactionalFailureService;
    @Autowired DataSource dataSource;

    @Test
    void postgresMcpOwnershipIdempotencyAuditAndFlywayEvidence() throws Exception {
        assertEquals(7, db.queryForObject("select count(*) from flyway_schema_history where success=true and version in ('1','2','3','4','5','6','7')", Integer.class));
        // The MCP adapter serves the deployment-fixed demo owner, so the seed must be
        // created under that owner or every MCP call would deny it.
        var data = newReminderSeed("mcp", "demo-owner");
        String id = data.reminder().id().toString();
        // The single-owner noauth contract: Principal and X-User-Id never change ownership,
        // so any caller still resolves to the demo owner and these all succeed.
        // Read-only calls prove a hostile identity cannot steal or deny the demo-owner scope;
        // the mutation evidence below uses correctly versioned, mutually non-conflicting flows.
        JsonNode ownerList = mcp("list_reminders", "{}", "bob", "alice");
        assertFalse(ownerList.has("error"), "list_reminders must run as the demo owner");
        assertTrue(ownerList.path("result").path("structuredContent").size() >= 1);
        JsonNode readAsBob = mcp("get_reminder", "{\"reminderId\":\"" + id + "\"}", "bob", "alice");
        assertFalse(readAsBob.has("error"), "get_reminder must run as the demo owner");
        assertEquals(id, readAsBob.path("result").path("structuredContent").path("id").asText());
        JsonNode statusAsBob = mcp("get_delivery_status", "{\"reminderId\":\"" + id + "\"}", "bob", "alice");
        assertFalse(statusAsBob.has("error"), "get_delivery_status must run as the demo owner");
        // get_delivery_status returns an empty list for a reminder with no attempts; that is
        // still a successful demo-owner resolution (no error), not an ownership denial.
        assertTrue(statusAsBob.path("result").path("structuredContent").isArray());
        // A second demo-owner reminder is used for the write flow so the update and cancel
        // calls run against fresh version 0 state without conflicting with the read-only seed.
        var write = newReminderSeed("mcp-write", "demo-owner");
        String writeId = write.reminder().id().toString();
        // Mutating tools still resolve to the demo owner under a hostile Principal/X-User-Id:
        // update at version 0 succeeds and bumps the version, then cancel uses the bumped
        // version so the optimistic lock never goes stale.
        String updateArgs = "{\"reminderId\":\"" + writeId + "\",\"eventId\":\"" + write.event().id() + "\",\"policyId\":\"" + write.policy().id() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"pg-update\"}";
        JsonNode updated = mcp("update_reminder", updateArgs, "bob", "alice");
        assertFalse(updated.has("error"), "update_reminder must run as the demo owner");
        assertEquals(1, updated.path("result").path("structuredContent").path("version").asInt());
        JsonNode cancelled = mcp("cancel_reminder", "{\"reminderId\":\"" + writeId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"pg-cancel\"}", "bob", "alice");
        assertFalse(cancelled.has("error"), "cancel_reminder must run as the demo owner");
        assertEquals("CANCELLED", cancelled.path("result").path("structuredContent").path("status").asText());
        String createArgs = "{\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"idempotencyKey\":\"pg-create\"}";
        JsonNode first = mcp("create_reminder", createArgs, null, null);
        JsonNode retry = mcp("create_reminder", createArgs, null, null);
        assertEquals(first.toString(), retry.toString());
        assertEquals(3, db.queryForObject("select count(*) from reminders", Integer.class));
        assertTrue(db.queryForObject("select count(*) from mcp_audit where user_id='demo-owner'", Integer.class) >= 3);
        assertEquals(0, db.queryForObject("select count(*) from mcp_audit where user_id='bob'", Integer.class));
        // Audit-reference clearing: the read-only seed reminder is still at version 0, so a
        // direct delete at version 0 succeeds and its single get_reminder audit row loses the
        // reminder reference via on delete set null. The hostile get_reminder above already
        // proved the demo-owner read contract, so no second get_reminder call is issued here.
        reminders.delete(data.reminder().id(), 0, "pg-delete");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where user_id='demo-owner' and tool_name='get_reminder' and reminder_id is null", Integer.class));
    }

    private record McpSeed(com.middleproject.reminder.domain.Event event, com.middleproject.reminder.domain.NotificationPolicy policy, com.middleproject.reminder.domain.Reminder reminder) {}
    private McpSeed newReminderSeed(String key, String owner) { var event = events.create(key, OffsetDateTime.now().plusHours(2), null, key + "-event-" + UUID.randomUUID()); var policy = policies.create("EMAIL", 5, key + "-policy-" + UUID.randomUUID()); return new McpSeed(event, policy, reminders.create(event.id(), policy.id(), key + "-reminder-" + UUID.randomUUID(), owner)); }
    private JsonNode mcp(String tool, String args, String user, String spoofed) throws Exception { var request = post("/api/mcp").contentType(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + args + "}}"); if (user != null) request.principal(() -> user); if (spoofed != null) request.header("X-User-Id", spoofed); return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("POSTGRES_TEST_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("trip.demo-owner-id", () -> "demo-owner");
    }

    @BeforeEach
    void verifyPostgres16AndClean() {
        Assumptions.assumeTrue(db.queryForObject("show server_version_num", String.class).startsWith("16"), "PostgreSQL 16 is required");
        mutableClock.set(Instant.now());
        cleanRows();
        scheduler.reset();
        notificationSender.reset();
    }

    @AfterEach
    void cleanAfter() { cleanRows(); }

    @Test
    void productionClaimAndReceiptConflictArePostgres16Compatible() {
        var event = events.create("postgres-test", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "pg-event-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 10, "pg-policy-" + UUID.randomUUID());
        var reminder = reminders.create(event.id(), policy.id(), "pg-reminder-" + UUID.randomUUID());

        makeAvailable(reminder.id());
        assertEquals(1, outbox.reconcile(10));
        assertEquals(1, scheduler.calls.get());
        String body = "{\"reminderId\":\"" + reminder.id() + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminder.id() + ":1\"}";
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(body));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, delivery.acceptResult(body));
    }

    @Test
    void duplicatePayloadsInvokeProviderOnceAcrossWorkers() throws Exception {
        var event = events.create("duplicate notification", OffsetDateTime.parse("2030-01-01T10:00:00Z"), null, "event-notification-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 10, "policy-notification-" + UUID.randomUUID());
        var reminder = reminders.create(event.id(), policy.id(), "reminder-notification-" + UUID.randomUUID());
        makeAvailable(reminder.id());
        assertEquals(1, outbox.reconcile(10));
        String body = "{\"reminderId\":\"" + reminder.id() + "\",\"schedulerVersion\":1,\"idempotencyKey\":\"" + reminder.id() + ":1\"}";
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, delivery.acceptResult(body));

        notificationSender.blockFirst = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<NotificationDeliveryService.AttemptResult> first = executor.submit(() -> notifications.deliver(body));
            assertTrue(notificationSender.firstCallEntered.await(5, TimeUnit.SECONDS));
            CountDownLatch secondWorkerStarted = new CountDownLatch(1);
            Future<NotificationDeliveryService.AttemptResult> second = executor.submit(() -> {
                secondWorkerStarted.countDown();
                return notifications.deliver(body);
            });
            assertTrue(secondWorkerStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            notificationSender.releaseFirst.countDown();
            assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).status());
            assertEquals("ALREADY_DELIVERED", second.get(5, TimeUnit.SECONDS).status());
            assertEquals(1, notificationSender.calls.get());
            assertEquals(1, db.queryForObject("select count(*) from notification_attempt", Integer.class));
            assertEquals("DELIVERED", db.queryForObject("select status from reminders where id=?", String.class, reminder.id()));
        } finally {
            notificationSender.releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            notificationSender.blockFirst = false;
        }
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

    @Test
    void concurrentSameKeyEventHttpCreateReturnsOneIdenticalResponseAndOneRow() throws Exception {
        String key = "pg-http-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("title", "concurrent-" + UUID.randomUUID());
            put("startsAt", "2030-01-01T10:00:00Z");
            put("endsAt", null);
        }});
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResult> first = executor.submit(() -> performCreate(start, key, body));
            Future<HttpResult> second = executor.submit(() -> performCreate(start, key, body));
            start.countDown();
            HttpResult firstResult = first.get(10, TimeUnit.SECONDS);
            HttpResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(200, firstResult.status());
            assertEquals(200, secondResult.status());
            assertEquals(firstResult.body(), secondResult.body());
            assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
        } finally { executor.shutdownNow(); }
    }

    private HttpResult performCreate(CountDownLatch start, String key, String body) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/events")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        return new HttpResult(response.getStatus(), response.getContentAsString());
    }

    private record HttpResult(int status, String body) {}

    @Test
    void postgresLeaseExpiresOnlyForSameHashAndSecondClaimLoses() {
        String scope = "pg-lease", key = "pg-lease-" + UUID.randomUUID(), hash = "hash-" + UUID.randomUUID();
        mutableClock.set(Instant.parse("2030-01-01T00:00:00Z"));
        assertTrue(idempotencyAdapter.reserve(scope, key, hash) != null);
        assertTrue(idempotencyAdapter.claimExpired(scope, key, hash) == null);
        mutableClock.advance(java.time.Duration.ofSeconds(31));
        assertTrue(idempotencyAdapter.claimExpired(scope, key, hash) != null);
        assertEquals(hash, idempotencyAdapter.find(scope, key).orElseThrow().requestHash());
        assertTrue(idempotencyAdapter.claimExpired(scope, key, hash) == null);
    }

    @Test
    void outerTransactionalFailureRollsBackBusinessInsertAndAllowsRetry() {
        String key = "pg-rollback-" + UUID.randomUUID();
        OffsetDateTime starts = OffsetDateTime.parse("2030-01-01T10:00:00Z");
        assertThrows(IllegalStateException.class, () -> transactionalFailureService.fail(key, starts));
        assertEquals(0, db.queryForObject("select count(*) from events", Integer.class));
        assertEquals("FAILED", db.queryForObject("select status from idempotency_record where scope=? and idempotency_key=?", String.class, "events:create", key));
        assertEquals("retry", events.create("retry", starts, null, key).title());
    }

    @Test
    void leaseMigrationHandlesMaxLengthLegacyInProgressKey() {
        String schema = "phase08_migration_" + UUID.randomUUID().toString().replace("-", "");
        String key = "k".repeat(200);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target(MigrationVersion.fromVersion("4")).load().migrate();
            db.update("insert into " + schema + ".idempotency_record"
                            + "(scope,idempotency_key,request_hash,status,attempts,created_at) values(?,?,?,?,?,?)",
                    "legacy", key, "h".repeat(64), "IN_PROGRESS", 0, OffsetDateTime.now(mutableClock));

            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();

            assertEquals(1, db.queryForObject("select count(*) from " + schema
                    + ".idempotency_record where claim_token is not null and char_length(claim_token) <= 36", Integer.class));
        } finally {
            db.execute("drop schema if exists " + schema + " cascade");
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

    private void makeAvailable(UUID reminderId) {
        db.update("update schedule_outbox set available_at=? where reminder_id=?", OffsetDateTime.now().minusMinutes(1), reminderId);
    }

    private void cleanRows() {
        db.update("delete from mcp_audit");
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from trip_outbox");
        db.update("delete from reminders");
        db.update("delete from notification_policies");
        db.update("delete from trip_events");
        db.update("delete from trips");
        db.update("delete from events");
        db.update("delete from idempotency_record");
    }

    @TestConfiguration
    static class Configuration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
        @Bean RecordingScheduler recordingScheduler() { return new RecordingScheduler(); }
        @Bean BlockingSender blockingSender() { return new BlockingSender(); }
        @Bean @Primary SchedulerPort schedulerPort(RecordingScheduler scheduler) { return scheduler; }
        @Bean @Primary NotificationSender notificationSender(BlockingSender sender) { return sender; }
    }

    @TestConfiguration
    static class TransactionalFailureConfiguration {
        @Bean TransactionalFailureService transactionalFailureService(IdempotencyService idempotency, JdbcTemplate db) { return new TransactionalFailureService(idempotency, db); }
    }

    static class TransactionalFailureService {
        private final IdempotencyService idempotency; private final JdbcTemplate db;
        TransactionalFailureService(IdempotencyService idempotency, JdbcTemplate db) { this.idempotency = idempotency; this.db = db; }
        @Transactional public void fail(String key, OffsetDateTime starts) {
            idempotency.execute("events:create", key, new Object[]{"retry", starts, null}, String.class, () -> {
                db.update("insert into events(id,title,starts_at,version,created_at,updated_at) values(?,?,?,0,?,?)", UUID.randomUUID(), "retry", starts, starts, starts);
                throw new IllegalStateException("deliberate rollback");
            });
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        void set(Instant value) { current.set(value); }
        void advance(java.time.Duration duration) { current.updateAndGet(value -> value.plus(duration)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current.get(); }
    }

    static class BlockingSender implements NotificationSender {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean blockFirst;
        volatile CountDownLatch firstCallEntered = new CountDownLatch(1);
        volatile CountDownLatch releaseFirst = new CountDownLatch(1);

        public Channel channel() { return Channel.EMAIL; }

        void reset() {
            calls.set(0);
            blockFirst = false;
            firstCallEntered = new CountDownLatch(1);
            releaseFirst = new CountDownLatch(1);
        }

        public SendResult send(SendRequest request) {
            calls.incrementAndGet();
            if (blockFirst) {
                firstCallEntered.countDown();
                try { releaseFirst.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return new SendResult("postgres-provider");
        }
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
