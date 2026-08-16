package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.port.DeliveryStatusRepository;
import com.middleproject.reminder.port.DeviceReminderProjectionRepository;
import com.middleproject.reminder.port.ReminderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owner-scoped read model for the Android companion. The controller stays thin:
 * every alarm/trip/delivery projection query lives here behind
 * {@link DeviceReminderProjectionRepository} and
 * {@link DeliveryStatusRepository} instead of raw SQL in the web layer.
 */
@Service
public class DeviceQueryService {

    private final ReminderRepository reminders;
    private final DeviceReminderProjectionRepository projections;
    private final DeliveryStatusRepository deliveryStatus;

    public DeviceQueryService(ReminderRepository reminders, DeviceReminderProjectionRepository projections,
                              DeliveryStatusRepository deliveryStatus) {
        this.reminders = reminders;
        this.projections = projections;
        this.deliveryStatus = deliveryStatus;
    }

    public List<ReminderView> reminders(String ownerId) {
        return reminders.findAllByOwner(ownerId).stream().map(this::view).toList();
    }

    public ReminderView findReminder(UUID id, String ownerId) {
        Reminder reminder = reminders.findByIdForOwner(id, ownerId)
                .orElseThrow(() -> notFound());
        return view(reminder);
    }

    /**
     * Typed delivery attempts for one reminder, owner-scoped. attemptedAt is
     * completedAt when present, otherwise createdAt, so the Android companion always
     * has a concrete delivery timestamp for each bounded entry.
     */
    public List<DeliveryView> delivery(UUID id, String ownerId) {
        reminders.findByIdForOwner(id, ownerId).orElseThrow(() -> notFound());
        return deliveryStatus.findDeliveryAttempts(id).stream()
                .map(attempt -> new DeliveryView(attempt.channel(), attempt.status(),
                        attempt.completedAt() != null ? attempt.completedAt() : attempt.createdAt()))
                .toList();
    }

    /** The alarm time is the event start minus the policy lead, computed from the source of truth. */
    private ReminderView view(Reminder reminder) {
        OffsetDateTime alarmTime = projections.alarmTime(reminder.id())
                .orElseThrow(() -> new IllegalStateException("Reminder " + reminder.id() + " has no event/policy projection"));
        return new ReminderView(reminder.id(), reminder.eventId(), reminder.policyId(),
                projections.tripId(reminder.id()).orElse(null), reminder.status().name(), reminder.version(), alarmTime);
    }

    public record ReminderView(UUID id, UUID eventId, UUID policyId, UUID tripId, String status,
                               long version, OffsetDateTime alarmTime) {}

    public record DeliveryView(String channel, String status, OffsetDateTime attemptedAt) {}

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found");
    }
}
