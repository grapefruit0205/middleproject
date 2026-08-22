package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripEvent;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.infrastructure.provider.ProviderCallPolicyClient;
import com.middleproject.reminder.port.PrivateCarRouteRepository;
import com.middleproject.reminder.port.TripEventRepository;
import com.middleproject.reminder.port.TripOutboxRepository;
import com.middleproject.reminder.port.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Private-car vertical slice application service. Transport adapters call exactly
 * these methods; no other code path can change the trip or write confirmed routes.
 */
@Service
public class PrivateCarPlanningService {

    public static final Duration PREVIEW_TTL = Duration.ofMinutes(10);
    /** Exact shape of a SHA-256 hex digest; proposal ids are validated before any provider or idempotency work. */
    public static final String PROPOSAL_ID_PATTERN = "[0-9a-f]{64}";
    private static final String NOTIFICATION_CHANNEL = "EMAIL";
    private static final String ROUTE_CONFIRMED_EVENT = "PRIVATE_CAR_ROUTE_CONFIRMED";

    private final TripRepository trips;
    private final TripEventRepository tripEvents;
    private final TripOutboxRepository outbox;
    private final PrivateCarRouteRepository routes;
    private final ProviderCallPolicyClient providers;
    private final IdempotencyService idempotency;
    private final DemoOwnerContext demoOwner;
    private final JdbcTemplate db;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PrivateCarPlanningService(TripRepository trips, TripEventRepository tripEvents,
                                     TripOutboxRepository outbox, PrivateCarRouteRepository routes,
                                     ProviderCallPolicyClient providers,
                                     IdempotencyService idempotency, DemoOwnerContext demoOwner,
                                     JdbcTemplate db, ObjectMapper objectMapper, Clock clock) {
        this.trips = trips; this.tripEvents = tripEvents; this.outbox = outbox; this.routes = routes;
        this.providers = providers; this.idempotency = idempotency;
        this.demoOwner = demoOwner; this.db = db; this.objectMapper = objectMapper; this.clock = clock;
    }

    /** One missing question at a time, or null when the input is complete. */
    public String nextQuestion(UUID tripId) {
        Trip trip = owned(tripId);
        return PrivateCarPlanningInput.fromDraft(trip, null).missingQuestion();
    }

    /**
     * Read-only route preview: needs origin/destination/departureAt, persists nothing, and
     * returns a stable proposal id plus provenance.
     */
    @Transactional(readOnly = true)
    public PrivateCarRoute previewRoute(UUID tripId) {
        Trip trip = owned(tripId);
        PrivateCarPlanningInput input = PrivateCarPlanningInput.fromDraft(trip, null);
        if (input.missingQuestion() != null && !"private_car.reminder_lead_minutes".equals(input.missingQuestion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing private-car input: " + input.missingQuestion());
        }
        return fetchRoute(input);
    }

    /**
     * Confirms a previously previewed route. Rejects stale previews and mismatched proposals;
     * all provider/stale/mismatch failures persist nothing. A successful confirmation is one
     * transaction that transitions the trip DRAFT -> AWAITING_CONFIRMATION -> CONFIRMED and
     * stores exactly one confirmed route, event, reminder policy/reminder, and outbox row.
     *
     * <p>Syntax and range validation (proposal id shape, required fields, lead range) run before
     * idempotency so malformed requests never reserve an idempotency record. Freshness checks
     * (future/stale preview timestamps) run inside the idempotent action so a completed
     * confirmation can always be replayed with the same key and payload, even after the preview
     * TTL has passed; a first-time expired preview still fails before any business persistence.
     */
    @Transactional
    public ConfirmationResult confirmRoute(UUID tripId, String proposalId, OffsetDateTime previewFetchedAt,
                                           Integer reminderLeadMinutes, String confirmationId, String key) {
        String owner = demoOwner.ownerId();
        if (confirmationId == null || confirmationId.isBlank() || confirmationId.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmationId must be nonblank and at most 200 characters");
        }
        if (proposalId == null || !proposalId.matches(PROPOSAL_ID_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "proposalId must be a lowercase SHA-256 hex digest");
        }
        if (previewFetchedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previewFetchedAt is required");
        }
        if (reminderLeadMinutes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reminderLeadMinutes is required");
        }
        if (reminderLeadMinutes < PrivateCarPlanningInput.LEAD_MIN || reminderLeadMinutes > PrivateCarPlanningInput.LEAD_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "reminderLeadMinutes must be between " + PrivateCarPlanningInput.LEAD_MIN + " and " + PrivateCarPlanningInput.LEAD_MAX);
        }
        final int lead = reminderLeadMinutes;
        return idempotency.execute("private-car:confirm:" + tripId + ":" + owner, key,
                new Object[]{proposalId, previewFetchedAt, lead, confirmationId}, ConfirmationResult.class, () -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (previewFetchedAt.isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previewFetchedAt cannot be in the future");
            }
            if (PrivateCarRoute.stale(previewFetchedAt, PREVIEW_TTL, now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Private-car route preview has expired; request a new preview");
            }
            Trip current = trips.findByIdForOwner(tripId, owner).orElseThrow(() -> notFound());
            if (current.status() != TripStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Private-car route can only be confirmed on a DRAFT trip");
            }
            PrivateCarRoute route = refetchAndMatch(current, proposalId);
            return commit(current, route, lead, confirmationId);
        });
    }

    private PrivateCarRoute refetchAndMatch(Trip trip, String proposalId) {
        PrivateCarPlanningInput input = PrivateCarPlanningInput.fromDraft(trip, null);
        PrivateCarRoute fresh = fetchRoute(input);
        if (!fresh.stableId().equals(proposalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Private-car route preview is stale; request a new preview");
        }
        return fresh;
    }

    private PrivateCarRoute fetchRoute(PrivateCarPlanningInput input) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ProviderOutcome<GeoPoint> origin = providers.geocode(input.origin());
        if (!origin.success()) throw providerFailure("origin geocoding", origin);
        ProviderOutcome<GeoPoint> destination = providers.geocode(input.destination());
        if (!destination.success()) throw providerFailure("destination geocoding", destination);
        RoutePlanRequest request = new RoutePlanRequest(input.origin(), input.destination(), origin.value(),
                destination.value(), input.departureAt());
        ProviderOutcome<RoutePlan> plan = providers.plan(request);
        if (!plan.success()) throw providerFailure("route planning", plan);
        return PrivateCarRoute.fromPlan(input.origin(), input.destination(), input.departureAt(),
                plan.value(), now, PREVIEW_TTL);
    }

    private ConfirmationResult commit(Trip current, PrivateCarRoute route, int lead, String confirmationId) {
        Trip awaiting = current.toAwaitingConfirmation();
        if (!trips.transition(current.id(), TripStatus.DRAFT, TripStatus.AWAITING_CONFIRMATION,
                current.version(), null)) {
            throw conflict();
        }
        Trip confirmed = awaiting.confirm(confirmationId);
        if (!trips.transition(current.id(), TripStatus.AWAITING_CONFIRMATION, TripStatus.CONFIRMED,
                awaiting.version(), confirmationId)) {
            throw conflict();
        }
        try {
            routes.insert(current.id(), route, lead);
            UUID eventId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID reminderId = UUID.randomUUID();
            String title = "Trip to " + confirmed.destination();
            if (title.length() > 200) title = title.substring(0, 200);
            db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                    eventId, title, confirmed.departureAt(), confirmed.returnAt(), OffsetDateTime.now(clock), OffsetDateTime.now(clock));
            db.update("insert into notification_policies(id,channel,lead_minutes,trip_id,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                    policyId, NOTIFICATION_CHANNEL, lead, current.id(), OffsetDateTime.now(clock), OffsetDateTime.now(clock));
            db.update("insert into reminders(id,event_id,policy_id,trip_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)",
                    reminderId, eventId, policyId, current.id(), confirmed.ownerId(), ReminderStatus.SCHEDULE_PENDING.name(),
                    OffsetDateTime.now(clock), OffsetDateTime.now(clock));
            tripEvents.insert(new TripEvent(UUID.randomUUID(), current.id(), "AWAITING_CONFIRMATION", null, OffsetDateTime.now(clock)));
            tripEvents.insert(new TripEvent(UUID.randomUUID(), current.id(), "CONFIRMED", null, OffsetDateTime.now(clock)));
            tripEvents.insert(new TripEvent(UUID.randomUUID(), current.id(), ROUTE_CONFIRMED_EVENT, route.stableId(), OffsetDateTime.now(clock)));
            OffsetDateTime dueAt = route.recommendedDepartureAt().minusMinutes(lead);
            outbox.insert(UUID.randomUUID(), current.id(), "UPSERT", confirmed.version() - 1, confirmed.version(),
                    dueAt, payload(current.id(), confirmed.version()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Private-car route confirmation could not be persisted", e);
        }
        return new ConfirmationResult(confirmed, route, lead);
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

    private Trip owned(UUID tripId) {
        return trips.findByIdForOwner(tripId, demoOwner.ownerId()).orElseThrow(() -> notFound());
    }

    private ResponseStatusException providerFailure(String stage, ProviderOutcome<?> outcome) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Private-car provider unavailable during " + stage + ": " + outcome.kind());
    }

    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"); }

    public record ConfirmationResult(Trip trip, PrivateCarRoute route, int reminderLeadMinutes) {
    }
}
