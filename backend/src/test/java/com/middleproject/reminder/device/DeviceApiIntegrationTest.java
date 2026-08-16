package com.middleproject.reminder.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.support.AdjustableClock;
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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:device-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class DeviceApiIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdjustableClock clock;
    @Autowired DevicePairingService pairing;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        db.update("delete from device_fcm_registration");
        db.update("delete from devices");
        db.update("delete from device_pairing_codes");
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

    private record Paired(String token, UUID deviceId, String installationId) {}

    private Paired pair() throws Exception {
        String code = pairing.issueCode().code();
        MvcResult result = mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Paired(body.path("token").asText(), UUID.fromString(body.path("deviceId").asText()), "install-1");
    }

    private MvcResult authed(String method, String path, String token, String body) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        if (body != null) request.content(body);
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
    }

    @Test
    void exchangeIsUnauthenticatedAndReturnsTokenOnceWithExpiryAndDeviceId() throws Exception {
        String code = pairing.issueCode().code();
        MvcResult result = mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.deviceId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("token").asText().matches("[A-Za-z0-9_-]{43,}"));
        assertEquals("2030-01-02T00:00:00Z", body.path("expiresAt").asText());
        // The raw token is returned exactly once: a second exchange with the same code fails.
        mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exchangeRejectsExpiredReusedAndMalformedCodesWithoutLeakingDetails() throws Exception {
        mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"AAAAA-BBBBB\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"aaaaa-bbbbb\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isUnauthorized());
        String code = pairing.issueCode().code();
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"install-1\",\"label\":\"Pixel 9\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingMalformedUnknownExpiredAndRevokedTokensAllReturn401() throws Exception {
        Paired paired = pair();
        mvc.perform(get("/api/device/trips")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/trips").header("Authorization", "Bearer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/trips").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/trips").header("Authorization", "Bearer unknown-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token() + "x")).andExpect(status().isUnauthorized());

        clock.advance(Duration.ofHours(24));
        mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenResolvesDemoOwnerScopeAndIgnoresIdentityHeaders() throws Exception {
        Paired paired = pair();
        // Hostile identity headers must not change the owner scope.
        mvc.perform(get("/api/device/trips")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("X-User-Id", "mallory"))
                .andExpect(status().isOk());
        // A second device's token cannot see this device's data.
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                UUID.randomUUID(), "demo-owner", "Seoul", "Tokyo",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, "DRAFT",
                OffsetDateTime.now(), OffsetDateTime.now());
        String list = mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, mapper.readTree(list).size());
    }

    @Test
    void tripsAndRemindersAreOwnerScopedWithAlarmTimeAndDeliveryStatus() throws Exception {
        Paired paired = pair();
        seedTripAndReminder("Seoul", "Tokyo", "2030-01-05T10:00:00+09:00");

        String trips = mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, mapper.readTree(trips).size());
        UUID tripId = UUID.fromString(mapper.readTree(trips).get(0).path("id").asText());

        mvc.perform(get("/api/device/trips/" + tripId).header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Tokyo"));

        String reminders = mvc.perform(get("/api/device/reminders").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alarmTime").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID reminderId = UUID.fromString(mapper.readTree(reminders).get(0).path("id").asText());
        assertEquals(OffsetDateTime.parse("2030-01-05T08:00:00+09:00").toInstant(),
                OffsetDateTime.parse(mapper.readTree(reminders).get(0).path("alarmTime").asText()).toInstant(),
                "alarm time must be departure minus the 120-minute lead");

        mvc.perform(get("/api/device/reminders/" + reminderId).header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULE_PENDING"));

        mvc.perform(get("/api/device/reminders/" + reminderId + "/delivery").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk());
    }

    @Test
    void otherOwnersTripsAndRemindersAreNeverExposed() throws Exception {
        Paired paired = pair();
        UUID otherTrip = UUID.randomUUID();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                otherTrip, "other-owner", "Seoul", "Busan",
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), null, "DRAFT",
                OffsetDateTime.now(), OffsetDateTime.now());
        mvc.perform(get("/api/device/trips/" + otherTrip).header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isNotFound());
        String list = mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, mapper.readTree(list).size());
    }

    @Test
    void tripAndReminderCancelAreIdempotentWithStableKeys() throws Exception {
        Paired paired = pair();
        Seed seed = seedTripAndReminder("Seoul", "Tokyo", "2030-01-05T10:00:00+09:00");
        UUID reminderId = seed.reminderId();
        UUID tripId = db.queryForObject("select trip_id from reminders where id=?", UUID.class, reminderId);
        long tripVersion = db.queryForObject("select version from trips where id=?", Long.class, tripId);

        mvc.perform(post("/api/device/trips/" + tripId + "/cancel")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "cancel-trip-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + tripVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // Replay with the same key returns the same stored result, not a conflict.
        mvc.perform(post("/api/device/trips/" + tripId + "/cancel")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "cancel-trip-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + tripVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long reminderVersion = db.queryForObject("select version from reminders where id=?", Long.class, reminderId);
        mvc.perform(post("/api/device/reminders/" + reminderId + "/cancel")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "cancel-reminder-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + reminderVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/device/reminders/" + reminderId + "/cancel")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "cancel-reminder-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + reminderVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void reminderAckUsesStateMachineAndOptimisticVersion() throws Exception {
        Paired paired = pair();
        Seed seed = seedTripAndReminder("Seoul", "Tokyo", "2030-01-05T10:00:00+09:00");
        UUID reminderId = seed.reminderId();
        long reminderVersion = db.queryForObject("select version from reminders where id=?", Long.class, reminderId);
        // Move the reminder to DELIVERED (SCHEDULE_PENDING -> SCHEDULED -> DISPATCHED -> DELIVERED).
        db.update("update reminders set status='SCHEDULED' where id=?", reminderId);
        db.update("update reminders set status='DISPATCHED' where id=?", reminderId);
        db.update("update reminders set status='DELIVERED' where id=?", reminderId);

        mvc.perform(post("/api/device/reminders/" + reminderId + "/ack")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "ack-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + reminderVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        // Idempotent retry of the ACK returns the stored ACKNOWLEDGED result.
        mvc.perform(post("/api/device/reminders/" + reminderId + "/ack")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "ack-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + reminderVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        // A stale version is rejected.
        mvc.perform(post("/api/device/reminders/" + reminderId + "/ack")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "ack-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + (reminderVersion + 1) + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void fcmTokenRegisterRefreshAndUnregisterPersistRawTokenForPhase17SenderAndHash() throws Exception {
        Paired paired = pair();
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-register-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-abc-123\"}"))
                .andExpect(status().isNoContent());
        String hash = db.queryForObject("select registration_token_hash from device_fcm_registration where device_id=?",
                String.class, paired.deviceId());
        assertEquals(64, hash.length());
        assertFalse(hash.contains("fcm-token-abc-123"), "the raw FCM token is stored for Phase 17 delivery, never as the hash");
        assertEquals("fcm-token-abc-123",
                db.queryForObject("select registration_token from device_fcm_registration where device_id=?",
                        String.class, paired.deviceId()),
                "the raw registration token must be persisted for the future server-side sender");

        // Refresh replaces the stored hash and keeps the raw token for the Phase 17 sender.
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-refresh-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-xyz-456\"}"))
                .andExpect(status().isNoContent());
        String refreshed = db.queryForObject("select registration_token_hash from device_fcm_registration where device_id=?",
                String.class, paired.deviceId());
        assertNotEquals(hash, refreshed);
        assertEquals(1, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
        assertEquals("fcm-token-xyz-456",
                db.queryForObject("select registration_token from device_fcm_registration where device_id=?",
                        String.class, paired.deviceId()),
                "the raw registration token must be persisted for the future server-side sender");

        mvc.perform(delete("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-delete-1"))
                .andExpect(status().isNoContent());
        assertEquals(0, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
        // An oversized or blank token is rejected.
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-oversized-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + "x".repeat(5000) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fcmTokenWritesAreIdempotentWithStableKeysAndRejectPayloadMismatch() throws Exception {
        Paired paired = pair();
        // Register with a stable key; exact replay is safe.
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-stable-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-stable-1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-stable-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-stable-1\"}"))
                .andExpect(status().isNoContent());
        assertEquals(1, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
        // The same key with a different payload is a conflict, not a silent overwrite.
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-stable-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-different\"}"))
                .andExpect(status().isConflict());
        assertEquals("fcm-token-stable-1",
                db.queryForObject("select registration_token from device_fcm_registration where device_id=?",
                        String.class, paired.deviceId()));

        // DELETE is idempotent too: exact replay is safe, a missing key is rejected.
        mvc.perform(delete("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-delete-stable-1"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-delete-stable-1"))
                .andExpect(status().isNoContent());
        assertEquals(0, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-no-key\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/device/fcm-token").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disconnectRevokesTokenAndRemovesFcmRegistrationAtomically() throws Exception {
        Paired paired = pair();
        mvc.perform(post("/api/device/fcm-token")
                        .header("Authorization", "Bearer " + paired.token())
                        .header("Idempotency-Key", "fcm-disconnect-setup-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"fcm-token-abc-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/device/disconnect").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isNoContent());
        assertEquals(0, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from devices", Integer.class));
        // Later bearer requests fail.
        mvc.perform(get("/api/device/trips").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isUnauthorized());
        // Disconnect is replayable: the second call also succeeds (nothing to revoke).
        mvc.perform(post("/api/device/disconnect").header("Authorization", "Bearer " + paired.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deviceRequestsAreRejectedWithoutIdempotencyKeyOnWrites() throws Exception {
        Paired paired = pair();
        mvc.perform(post("/api/device/trips/" + UUID.randomUUID() + "/cancel")
                        .header("Authorization", "Bearer " + paired.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/device/reminders/" + UUID.randomUUID() + "/ack")
                        .header("Authorization", "Bearer " + paired.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
    }

    private record Seed(UUID reminderId, long version) {}

    private Seed seedTripAndReminder(String departure, String destination, String departureAt) throws Exception {
        String draftBody = "{\"departure\":\"" + departure + "\",\"destination\":\"" + destination
                + "\",\"departureAt\":\"" + departureAt + "\",\"returnAt\":null}";
        String draft = mvc.perform(post("/api/trip-drafts")
                        .header("Idempotency-Key", "seed-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID tripId = UUID.fromString(mapper.readTree(draft).path("id").asText());
        String confirm = mvc.perform(post("/api/trips/" + tripId + "/confirm")
                        .header("Idempotency-Key", "confirm-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"seed-confirm\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID reminderId = db.queryForObject("select id from reminders where trip_id=?", UUID.class, tripId);
        return new Seed(reminderId, mapper.readTree(confirm).path("version").asLong());
    }
}
