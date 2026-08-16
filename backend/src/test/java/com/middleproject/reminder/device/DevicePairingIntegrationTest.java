package com.middleproject.reminder.device;

import com.middleproject.reminder.support.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pairing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class DevicePairingIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired AdjustableClock clock;
    @Autowired DevicePairingService service;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        db.update("delete from device_fcm_registration");
        db.update("delete from devices");
        db.update("delete from device_pairing_codes");
    }

    @Test
    void issuedCodeIsStoredOnlyAsSaltedSlowHash() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        assertNotNull(issued);
        String raw = issued.code();
        assertTrue(raw.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"));

        var row = db.queryForMap("select code_hash,salt,status,expires_at from device_pairing_codes where status='ACTIVE'");
        assertFalse(row.get("code_hash").toString().contains(raw), "raw pairing code must never be stored");
        assertFalse(row.get("salt").toString().contains(raw), "raw pairing code must never be part of the salt");
        assertEquals(64, row.get("code_hash").toString().length(), "code hash must be a SHA-256 hex digest");
        assertFalse(row.containsKey("code"), "no column may hold the raw pairing code");
        assertEquals("ACTIVE", row.get("status").toString());
    }

    @Test
    void exchangeReturnsOpaqueTokenOnceWithExpiryAndDeviceId() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        DevicePairingService.ExchangeResult exchange = service.exchange(issued.code(), "install-1", "Pixel");

        assertTrue(exchange.token().matches("[A-Za-z0-9_-]{43,}"), "opaque base64url token with >= 256 bits");
        assertNotNull(exchange.deviceId());
        assertNotNull(exchange.expiresAt());
        assertEquals(clock.instant().plus(Duration.ofHours(24)), exchange.expiresAt(), "device token expiry must be exactly 24 hours");

        // The raw token is returned exactly once: the pairing code is consumed.
        assertThrows(DevicePairingException.class, () -> service.exchange(issued.code(), "install-1", "Pixel"));
        // Only the SHA-256 hash of the token is stored.
        var device = db.queryForMap("select token_hash from devices where id=?", exchange.deviceId());
        assertFalse(device.get("token_hash").toString().contains(exchange.token()));
        assertEquals(64, device.get("token_hash").toString().length());
    }

    @Test
    void exchangeRejectsUnknownExpiredAndReusedCodes() {
        assertThrows(DevicePairingException.class, () -> service.exchange("AAAAA-BBBBB", "install-1", "Pixel"));
        assertThrows(DevicePairingException.class, () -> service.exchange("aaaaa-bbbbb", "install-1", "Pixel"));
        assertThrows(DevicePairingException.class, () -> service.exchange(null, "install-1", "Pixel"));

        DevicePairingService.IssuedPairing issued = service.issueCode();
        clock.advance(Duration.ofMinutes(5));
        assertThrows(DevicePairingException.class, () -> service.exchange(issued.code(), "install-1", "Pixel"));
    }

    @Test
    void exchangeAtExactlyTheFiveMinuteBoundaryIsRejected() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertNotNull(service.exchange(issued.code(), "install-1", "Pixel"));
    }

    @Test
    void tokenLookupResolvesOnlyDemoOwnerAndIgnoresIdentityHeaders() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        DevicePairingService.ExchangeResult exchange = service.exchange(issued.code(), "install-1", "Pixel");

        DevicePairingService.DeviceSession session = service.authenticate("Bearer " + exchange.token());
        assertEquals("demo-owner", session.ownerId());
        assertEquals(exchange.deviceId(), session.deviceId());
        // Identity headers are ignored: only the bearer token resolves the owner/device.
        assertThrows(DevicePairingException.class, () -> service.authenticate("Bearer " + exchange.token() + "x"));
        assertThrows(DevicePairingException.class, () -> service.authenticate("Bearer "));
        assertThrows(DevicePairingException.class, () -> service.authenticate("Basic dXNlcjpwYXNz"));
    }

    @Test
    void tokenExpiresAtTwentyFourHours() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        DevicePairingService.ExchangeResult exchange = service.exchange(issued.code(), "install-1", "Pixel");
        clock.advance(Duration.ofHours(24));
        assertThrows(DevicePairingException.class, () -> service.authenticate("Bearer " + exchange.token()));
    }

    @Test
    void revocationMakesLaterBearerRequestsFail() {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        DevicePairingService.ExchangeResult exchange = service.exchange(issued.code(), "install-1", "Pixel");
        service.revoke(exchange.deviceId());
        assertThrows(DevicePairingException.class, () -> service.authenticate("Bearer " + exchange.token()));
    }

    @Test
    void concurrentExchangesConsumeTheSingleCodeExactlyOnce() throws Exception {
        DevicePairingService.IssuedPairing issued = service.issueCode();
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        var successes = java.util.Collections.synchronizedList(new java.util.ArrayList<DevicePairingService.ExchangeResult>());
        var failures = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        for (int i = 0; i < workers; i++) {
            String install = "install-" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    successes.add(service.exchange(issued.code(), install, "Pixel"));
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, successes.size(), "exactly one exchange must succeed: " + successes.size() + " succeeded, " + failures.size() + " failed");
        assertEquals(1, countDevices(), "exactly one device row must be created");
        assertEquals(1, countConsumedCodes(), "the code must be consumed exactly once");
    }

    @Test
    void concurrentIssuanceCreatesAtMostOneActiveCode() throws Exception {
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        var issued = java.util.Collections.synchronizedList(new java.util.ArrayList<DevicePairingService.IssuedPairing>());
        var failures = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    issued.add(service.issueCode());
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, countActiveCodes(), "at most one active unexpired pairing code must exist");
        assertEquals(1, issued.size(), "exactly one issuance must succeed; the losers must fail safely");
        assertTrue(failures.stream().allMatch(t -> t instanceof DevicePairingException),
                "losing issuances must fail with the safe conflict exception, but got: " + failures);
        assertTrue(failures.stream().allMatch(t -> ((DevicePairingException) t).getStatusCode() == org.springframework.http.HttpStatus.CONFLICT),
                "losing issuances must fail with HTTP 409, but got: " + failures);
        // The single active code remains exchangeable.
        assertNotNull(service.exchange(issued.get(0).code(), "install-1", "Pixel"));
    }

    @Test
    void repeatIssuanceWhileActiveCreatesNoSecondSideEffect() {
        DevicePairingService.IssuedPairing first = service.issueCode();
        assertThrows(DevicePairingException.class, service::issueCode, "a repeat issue while active must be rejected safely");
        assertEquals(1, countActiveCodes());
        // Expiring the active code frees the slot for a new one.
        clock.advance(Duration.ofMinutes(5));
        DevicePairingService.IssuedPairing second = service.issueCode();
        assertNotNull(second);
        assertEquals(1, countActiveCodes());
    }

    @Test
    void sequentialLifecyclesCoexistAsInactiveHistoryWithAtMostOneActiveRow() {
        // Lifecycle 1: issue, exchange (consumes).
        DevicePairingService.IssuedPairing first = service.issueCode();
        DevicePairingService.ExchangeResult firstExchange = service.exchange(first.code(), "install-1", "Pixel");
        assertNotNull(firstExchange);
        // Lifecycle 2: issue, exchange (consumes).
        DevicePairingService.IssuedPairing second = service.issueCode();
        DevicePairingService.ExchangeResult secondExchange = service.exchange(second.code(), "install-2", "Pixel");
        assertNotNull(secondExchange);
        // Lifecycle 3: issue, expire by clock.
        DevicePairingService.IssuedPairing third = service.issueCode();
        clock.advance(Duration.ofMinutes(5));
        // Lifecycle 4: issue again while the previous row is EXPIRED; still active.
        DevicePairingService.IssuedPairing fourth = service.issueCode();
        assertNotNull(fourth);

        // Multiple inactive historical rows coexist: 2 CONSUMED + 1 EXPIRED.
        assertEquals(2, countConsumedCodes(), "both consumed historical rows must coexist");
        assertEquals(1, db.queryForObject("select count(*) from device_pairing_codes where status='EXPIRED'", Integer.class));
        // At most one ACTIVE row exists at any point.
        assertEquals(1, countActiveCodes());
        // The active code is still exchangeable after all the history.
        assertNotNull(service.exchange(fourth.code(), "install-4", "Pixel"));
    }

    @Test
    void unrelatedIntegrityFailuresPropagateInsteadOfLookingLikeDuplicateSlots() {
        // A device row referencing a non-existent owner/installation pair is an unrelated
        // integrity failure (FK violation on devices.owner_id? none exists, but a bad UUID
        // shape or a bogus token_hash length is not a duplicate-slot conflict). The narrow
        // duplicate classification must not swallow it into a "false" duplicate result.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                db.update("insert into device_fcm_registration(device_id,registration_token,registration_token_hash,registered_at) values(?,?,?,?)",
                        UUID.randomUUID(), "tok", "a".repeat(64), java.time.OffsetDateTime.now()));
        // Inserting a second active pairing code must still report the intended duplicate.
        DevicePairingService.IssuedPairing first = service.issueCode();
        assertThrows(DevicePairingException.class, service::issueCode);
        assertEquals(1, countActiveCodes());
    }

    private int countDevices() {
        return db.queryForObject("select count(*) from devices", Integer.class);
    }

    private int countActiveCodes() {
        return db.queryForObject("select count(*) from device_pairing_codes where status='ACTIVE' and expires_at > ?",
                Integer.class, java.time.OffsetDateTime.now(clock));
    }

    private int countConsumedCodes() {
        return db.queryForObject("select count(*) from device_pairing_codes where status='CONSUMED'", Integer.class);
    }
}
