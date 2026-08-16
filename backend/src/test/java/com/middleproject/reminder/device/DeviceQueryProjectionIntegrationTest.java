package com.middleproject.reminder.device;

import com.middleproject.reminder.application.DeviceQueryService;
import com.middleproject.reminder.port.DeviceRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The alarm projection for Android must live behind an application query service and a
 * port/infrastructure adapter, not inside the web controller. These tests pin the
 * service API (owner-scoped, alarm time computed from events minus policy lead).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:device-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class DeviceQueryProjectionIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired DeviceQueryService queries;
    @Autowired DeviceRepository devices;
    @Autowired AdjustableClock clock;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        db.update("delete from device_fcm_registration");
        db.update("delete from devices");
        db.update("delete from notification_attempt");
        db.update("delete from schedule_outbox");
        db.update("delete from reminders");
        db.update("delete from notification_policies");
        db.update("delete from trip_events");
        db.update("delete from trips");
        db.update("delete from events");
    }

    private DeviceRepository.DeviceRow deviceRow(String installationId) {
        Instant now = clock.instant();
        return new DeviceRepository.DeviceRow(UUID.randomUUID(), "demo-owner", installationId, "Pixel",
                "a".repeat(64), "ACTIVE", now.plus(Duration.ofHours(24)), now, null);
    }

    @Test
    void alarmTimeComesFromEventStartMinusPolicyLead() {
        UUID tripId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                tripId, "demo-owner", "Seoul", "Tokyo", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                "CONFIRMED", OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, "Trip to Tokyo", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                policyId, "EMAIL", 120, tripId, OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)",
                reminderId, eventId, policyId, tripId, "demo-owner", "SCHEDULED",
                OffsetDateTime.now(), OffsetDateTime.now());

        DeviceQueryService.ReminderView view = queries.findReminder(reminderId, "demo-owner");
        assertEquals(tripId, view.tripId());
        assertEquals("SCHEDULED", view.status());
        assertEquals(0, view.version());
        assertNotNull(view.alarmTime(), "alarm time must be projected by the query service");
        assertEquals(OffsetDateTime.parse("2030-01-05T08:00:00+09:00").toInstant(), view.alarmTime().toInstant(),
                "alarm time must be event start minus the 120-minute policy lead");
    }

    @Test
    void reminderViewIsOwnerScoped() {
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, "Other owner event", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,null,?,?,0)",
                policyId, "EMAIL", 120, OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,null,?,?,?,?,0)",
                reminderId, eventId, policyId, "other-owner", "SCHEDULED",
                OffsetDateTime.now(), OffsetDateTime.now());

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> queries.findReminder(reminderId, "demo-owner"));
    }

    @Test
    void reminderDeliveryViewCarriesChannelStatusAndAttemptedAt() {
        UUID tripId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.parse("2030-01-04T09:00:00+09:00");
        OffsetDateTime completed = OffsetDateTime.parse("2030-01-05T09:15:00+09:00");
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                tripId, "demo-owner", "Seoul", "Tokyo", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                "CONFIRMED", OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, "Trip to Tokyo", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                policyId, "PUSH", 120, tripId, OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)",
                reminderId, eventId, policyId, tripId, "demo-owner", "DELIVERED",
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_attempt(id,reminder_id,correlation_id,delivery_key,channel,recipient,status,created_at,completed_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), reminderId, UUID.randomUUID(), "delivery-key-1", "PUSH", "device-token", "DELIVERED",
                created, completed);

        List<DeviceQueryService.DeliveryView> delivery = queries.delivery(reminderId, "demo-owner");

        assertEquals(1, delivery.size());
        DeviceQueryService.DeliveryView view = delivery.get(0);
        assertEquals("PUSH", view.channel());
        assertEquals("DELIVERED", view.status());
        assertNotNull(view.attemptedAt(), "attemptedAt must fall back to createdAt when completedAt is absent");
        assertEquals(completed.toInstant(), view.attemptedAt().toInstant(),
                "attemptedAt must be completedAt when present");
    }

    @Test
    void reminderDeliveryAttemptedAtFallsBackToCreatedAtWhenNotCompleted() {
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.parse("2030-01-04T09:00:00+09:00");
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, "Trip to Tokyo", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,null,?,?,0)",
                policyId, "EMAIL", 120, OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,null,?,?,?,?,0)",
                reminderId, eventId, policyId, "demo-owner", "SCHEDULED",
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_attempt(id,reminder_id,correlation_id,delivery_key,channel,recipient,status,created_at,completed_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), reminderId, UUID.randomUUID(), "delivery-key-2", "EMAIL", "a@example.com", "RETRYING",
                created, null);

        List<DeviceQueryService.DeliveryView> delivery = queries.delivery(reminderId, "demo-owner");

        assertEquals(1, delivery.size());
        assertEquals("EMAIL", delivery.get(0).channel());
        assertEquals("RETRYING", delivery.get(0).status());
        assertEquals(created.toInstant(), delivery.get(0).attemptedAt().toInstant());
    }

    @Test
    void reminderDeliveryIsOwnerScoped() {
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, "Other owner event", OffsetDateTime.parse("2030-01-05T10:00:00+09:00"), null,
                OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,null,?,?,0)",
                policyId, "EMAIL", 120, OffsetDateTime.now(), OffsetDateTime.now());
        db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,null,?,?,?,?,0)",
                reminderId, eventId, policyId, "other-owner", "SCHEDULED",
                OffsetDateTime.now(), OffsetDateTime.now());

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> queries.delivery(reminderId, "demo-owner"));
    }

    @Test
    void fcmRegistrationStoresTheActualTokenForDeliveryPlusHashForLookup() {
        DeviceRepository.DeviceRow row = deviceRow("install-fcm");
        assertTrue(devices.insert(row));
        UUID deviceId = row.id();
        devices.upsertFcmRegistration(deviceId, "registration-token-12345", "a".repeat(64), clock.instant());

        Optional<String> forDelivery = devices.findFcmRegistrationToken(deviceId);
        assertTrue(forDelivery.isPresent(), "lookup-for-delivery must return the raw registration token");
        assertEquals("registration-token-12345", forDelivery.get());

        // The hash column exists and matches, so uniqueness/querying by hash still works.
        String hash = db.queryForObject("select registration_token_hash from device_fcm_registration where device_id=?",
                String.class, deviceId);
        assertEquals("a".repeat(64), hash);

        // Refresh replaces both the token and the hash, keeping a single row.
        devices.upsertFcmRegistration(deviceId, "registration-token-67890", "b".repeat(64), clock.instant());
        assertEquals("registration-token-67890", devices.findFcmRegistrationToken(deviceId).orElseThrow());
        assertEquals(1, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));

        devices.deleteFcmRegistration(deviceId);
        assertFalse(devices.findFcmRegistrationToken(deviceId).isPresent());
        assertEquals(0, db.queryForObject("select count(*) from device_fcm_registration", Integer.class));
    }
}
