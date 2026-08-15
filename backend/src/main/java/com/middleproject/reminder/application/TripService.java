package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripEvent;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.port.TripEventRepository;
import com.middleproject.reminder.port.TripOutboxRepository;
import com.middleproject.reminder.port.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripService {
    private static final int LEAD_MINUTES = 120;
    private static final String NOTIFICATION_CHANNEL = "EMAIL";

    private final TripRepository trips;
    private final TripEventRepository tripEvents;
    private final TripOutboxRepository outbox;
    private final IdempotencyService idempotency;
    private final JdbcTemplate db;
    private final ObjectMapper objectMapper;
    private final DemoOwnerContext demoOwner;

    public TripService(TripRepository trips, TripEventRepository tripEvents, TripOutboxRepository outbox,
                       IdempotencyService idempotency, JdbcTemplate db, ObjectMapper objectMapper,
                       DemoOwnerContext demoOwner) {
        this.trips = trips; this.tripEvents = tripEvents; this.outbox = outbox;
        this.idempotency = idempotency; this.db = db; this.objectMapper = objectMapper; this.demoOwner = demoOwner;
    }

    public List<Trip> all() { return trips.findAllByOwner(demoOwner.ownerId()); }
    public Trip find(UUID id) { return trips.findByIdForOwner(id, demoOwner.ownerId()).orElseThrow(() -> notFound()); }

    @Transactional
    public Trip createDraft(String departure, String destination, OffsetDateTime departureAt, OffsetDateTime returnAt, String key) {
        return createDraft(departure, destination, departureAt, returnAt, key, null);
    }

    @Transactional
    public Trip createDraft(String departure, String destination, OffsetDateTime departureAt, OffsetDateTime returnAt,
                            String key, String clientOwnerId) {
        validateDraft(departure, destination, departureAt, returnAt);
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:create:" + owner, key,
                new Object[]{departure, destination, departureAt, returnAt}, Trip.class, () -> {
            Trip draft = trips.insert(UUID.randomUUID(), owner, departure, destination, departureAt, returnAt, TripStatus.DRAFT);
            tripEvents.insert(new TripEvent(UUID.randomUUID(), draft.id(), "DRAFT_CREATED", null, OffsetDateTime.now()));
            return draft;
        });
    }

    @Transactional
    public Trip answerQuestion(UUID id, String questionId, String answer, String key) {
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:answer:" + id + ":" + owner, key, new Object[]{questionId, answer}, Trip.class, () -> {
            Trip current = trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
            Trip next = current.answer(questionId, answer);
            if (!trips.addDraftAnswer(id, questionId, answer, toJson(next.draftContext()), current.version())) {
                throw conflict();
            }
            TripEvent event = new TripEvent(UUID.randomUUID(), id, "DRAFT_ANSWERED", questionId, OffsetDateTime.now());
            tripEvents.insert(event);
            return trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
        });
    }

    @Transactional
    public Trip confirm(UUID id, String confirmationId, String key) {
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:confirm:" + id + ":" + owner, key, new Object[]{confirmationId}, Trip.class, () -> {
            Trip current = trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
            Trip awaiting = current.toAwaitingConfirmation();
            if (!trips.transition(id, TripStatus.DRAFT, TripStatus.AWAITING_CONFIRMATION, current.version(), null)) {
                throw conflict();
            }
            Trip confirmed = awaiting.confirm(confirmationId);
            if (!trips.transition(id, TripStatus.AWAITING_CONFIRMATION, TripStatus.CONFIRMED, awaiting.version(), confirmationId)) {
                throw conflict();
            }
            persistConfirmed(id, confirmed);
            tripEvents.insert(new TripEvent(UUID.randomUUID(), id, "AWAITING_CONFIRMATION", null, OffsetDateTime.now()));
            tripEvents.insert(new TripEvent(UUID.randomUUID(), id, "CONFIRMED", null, OffsetDateTime.now()));
            return trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
        });
    }

    @Transactional
    public Trip cancel(UUID id, long expectedVersion, String key) {
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:cancel:" + id + ":" + owner, key, new Object[]{expectedVersion}, Trip.class, () -> {
            Trip current = trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
            if (current.version() != expectedVersion) throw conflict();
            Trip cancelled = current.cancel();
            if (!trips.transition(id, current.status(), TripStatus.CANCELLED, current.version(), null)) throw conflict();
            tripEvents.insert(new TripEvent(UUID.randomUUID(), id, "CANCELLED", null, OffsetDateTime.now()));
            outbox.insert(UUID.randomUUID(), id, "DELETE", cancelled.version(), cancelled.version(),
                    OffsetDateTime.now(), payload(id, cancelled.version()));
            return trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
        });
    }

    @Transactional
    public Trip expire(UUID id, long expectedVersion, String key) {
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:expire:" + id + ":" + owner, key, new Object[]{expectedVersion}, Trip.class, () -> {
            Trip current = trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
            if (current.version() != expectedVersion) throw conflict();
            Trip expired = current.expire();
            if (!trips.transition(id, current.status(), TripStatus.EXPIRED, current.version(), null)) throw conflict();
            tripEvents.insert(new TripEvent(UUID.randomUUID(), id, "EXPIRED", null, OffsetDateTime.now()));
            return trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
        });
    }

    @Transactional
    public Trip restart(UUID id, long expectedVersion, String key) {
        String owner = demoOwner.ownerId();
        return idempotency.execute("trips:restart:" + id + ":" + owner, key, new Object[]{expectedVersion}, Trip.class, () -> {
            Trip current = trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
            if (current.version() != expectedVersion) throw conflict();
            Trip restarted = current.restart();
            if (!trips.transition(id, current.status(), TripStatus.DRAFT, current.version(), null)) throw conflict();
            tripEvents.insert(new TripEvent(UUID.randomUUID(), id, "RESTARTED", null, OffsetDateTime.now()));
            return trips.findByIdForOwner(id, owner).orElseThrow(() -> notFound());
        });
    }

    private void validateDraft(String departure, String destination, OffsetDateTime departureAt, OffsetDateTime returnAt) {
        if (departure == null || departure.isBlank() || departure.length() > 200
                || destination == null || destination.isBlank() || destination.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departure and destination must be nonblank and at most 200 characters");
        }
        if (departureAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departureAt is required");
        }
        if (returnAt != null && returnAt.isBefore(departureAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "returnAt must be greater than or equal to departureAt");
        }
    }

    private void persistConfirmed(UUID tripId, Trip confirmed) {
        try {
            UUID eventId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID reminderId = UUID.randomUUID();
            String title = "Trip to " + confirmed.destination();
            if (title.length() > 200) title = title.substring(0, 200);
            db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                    eventId, title, confirmed.departureAt(), confirmed.returnAt(), OffsetDateTime.now(), OffsetDateTime.now());
            db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                    policyId, NOTIFICATION_CHANNEL, LEAD_MINUTES, tripId, OffsetDateTime.now(), OffsetDateTime.now());
            db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)",
                    reminderId, eventId, policyId, tripId, confirmed.ownerId(), ReminderStatus.SCHEDULE_PENDING.name(),
                    OffsetDateTime.now(), OffsetDateTime.now());
            outbox.insert(UUID.randomUUID(), tripId, "UPSERT", confirmed.version() - 1, confirmed.version(),
                    confirmed.departureAt().minusMinutes(LEAD_MINUTES), payload(tripId, confirmed.version()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip confirmation could not be persisted", e);
        }
    }

    private String payload(UUID tripId, long schedulerVersion) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tripId", tripId.toString(),
                    "schedulerVersion", schedulerVersion,
                    "idempotencyKey", tripId + ":" + schedulerVersion));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Map<String, String> context) {
        try { return objectMapper.writeValueAsString(context); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"); }
}
