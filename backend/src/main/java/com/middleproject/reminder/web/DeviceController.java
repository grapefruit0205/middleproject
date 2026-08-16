package com.middleproject.reminder.web;

import com.middleproject.reminder.application.DeviceQueryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.device.DevicePairingService;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.Trip;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Public Android pairing/device REST boundaries. Every /api/device/** request except the
 * unauthenticated exchange is protected by the bearer-token interceptor, which resolves
 * only the stored Demo Owner/device and ignores identity headers. All writes use
 * Idempotency-Key except the deliberately one-time pairing exchange and disconnect.
 */
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final DevicePairingService pairing;
    private final DeviceQueryService queries;
    private final TripService trips;
    private final ReminderService reminders;

    public DeviceController(DevicePairingService pairing, DeviceQueryService queries, TripService trips,
                            ReminderService reminders) {
        this.pairing = pairing;
        this.queries = queries;
        this.trips = trips;
        this.reminders = reminders;
    }

    public record ExchangeRequest(
            @NotBlank @Size(max = 20) String pairingCode,
            @NotBlank @Size(max = 200) String installationId,
            @NotBlank @Size(max = 200) String label) {}

    public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    public record FcmTokenRequest(@NotBlank @Size(max = 4096) String registrationToken) {}

    /** Unauthenticated: accepts only the pairing code plus installation id and label. */
    @PostMapping("/exchange")
    public ExchangeResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        DevicePairingService.ExchangeResult result = pairing.exchange(
                request.pairingCode().trim().toUpperCase(),
                request.installationId().trim(),
                request.label().trim());
        return new ExchangeResponse(result.token(), result.deviceId(),
                OffsetDateTime.ofInstant(result.expiresAt(), java.time.ZoneOffset.UTC));
    }

    public record ExchangeResponse(String token, UUID deviceId, OffsetDateTime expiresAt) {}

    @GetMapping("/trips")
    public List<Trip> trips() {
        return trips.all();
    }

    @GetMapping("/trips/{id}")
    public Trip trip(@PathVariable UUID id) {
        return trips.find(id);
    }

    @PostMapping("/trips/{id}/cancel")
    public Trip cancelTrip(@PathVariable UUID id,
                           @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                           @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return trips.cancel(id, request.expectedVersion(), key);
    }

    @GetMapping("/reminders")
    public List<DeviceQueryService.ReminderView> reminders() {
        return queries.reminders(pairing.ownerId());
    }

    @GetMapping("/reminders/{id}")
    public DeviceQueryService.ReminderView reminder(@PathVariable UUID id) {
        return queries.findReminder(id, pairing.ownerId());
    }

    @GetMapping("/reminders/{id}/delivery")
    public List<DeviceQueryService.DeliveryView> reminderDelivery(@PathVariable UUID id) {
        return queries.delivery(id, pairing.ownerId());
    }

    @PostMapping("/reminders/{id}/cancel")
    public DeviceQueryService.ReminderView cancelReminder(@PathVariable UUID id,
                                                          @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                          @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        Reminder reminder = reminders.transition(id, ReminderStatus.CANCELLED, request.expectedVersion(), key, pairing.ownerId());
        return queries.findReminder(id, pairing.ownerId());
    }

    /** ACK uses the existing Reminder state machine and optimistic version; never bypasses service/domain rules. */
    @PostMapping("/reminders/{id}/ack")
    public DeviceQueryService.ReminderView acknowledge(@PathVariable UUID id,
                                                       @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                       @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        reminders.transition(id, ReminderStatus.ACKNOWLEDGED, request.expectedVersion(), key, pairing.ownerId());
        return queries.findReminder(id, pairing.ownerId());
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<Void> registerFcmToken(@Valid @RequestBody FcmTokenRequest request,
                                                 @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                 HttpServletRequest http) {
        requireKey(key);
        pairing.registerFcmToken(session(http).deviceId(), request.registrationToken().trim(), key);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fcm-token")
    public ResponseEntity<Void> unregisterFcmToken(@RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                   HttpServletRequest http) {
        requireKey(key);
        pairing.unregisterFcmToken(session(http).deviceId(), key);
        return ResponseEntity.noContent().build();
    }

    /** Deliberately one-time: disconnect revokes the server token and removes FCM registration atomically. */
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(HttpServletRequest http) {
        pairing.disconnect(session(http).deviceId());
        return ResponseEntity.noContent().build();
    }

    private DevicePairingService.DeviceSession session(HttpServletRequest http) {
        return (DevicePairingService.DeviceSession) http.getAttribute("deviceSession");
    }

    private void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }
}
