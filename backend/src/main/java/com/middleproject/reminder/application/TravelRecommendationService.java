package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.FollowUpConsent;
import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceCategory;
import com.middleproject.reminder.domain.PlaceSearchRequest;
import com.middleproject.reminder.domain.PostTripRecommendationResult;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.domain.ProviderFailure;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.TravelContextResult;
import com.middleproject.reminder.domain.TravelRecommendationRules;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.WeatherForecast;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.port.PlaceSearchProviderPort;
import com.middleproject.reminder.port.PrivateCarRouteRepository;
import com.middleproject.reminder.port.TravelConsentRepository;
import com.middleproject.reminder.port.TripRepository;
import com.middleproject.reminder.port.WeatherProviderPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Travel follow-up recommendation application service. {@link #context} builds the travel
 * context (weather, packing items, accommodations) and proposes follow-up consent;
 * {@link #recordConsent} records an ACCEPTED/DECLINED decision idempotently;
 * {@link #recommend} produces the accepted recommendation. Provider failures are reported
 * as typed {@link ProviderFailure} records and never as exceptions.
 */
@Service
public class TravelRecommendationService {

    /** Confirmed route distance (meters) at and above which accommodation search runs. */
    public static final int ACCOMMODATION_MIN_DISTANCE = 200_000;

    private final TripRepository trips;
    private final PrivateCarRouteRepository routes;
    private final TravelConsentRepository consents;
    private final WeatherProviderPort weather;
    private final PlaceSearchProviderPort places;
    private final IdempotencyService idempotency;
    private final DemoOwnerContext demoOwner;

    public TravelRecommendationService(TripRepository trips, PrivateCarRouteRepository routes,
                                       TravelConsentRepository consents, WeatherProviderPort weather,
                                       PlaceSearchProviderPort places, IdempotencyService idempotency,
                                       DemoOwnerContext demoOwner) {
        this.trips = trips;
        this.routes = routes;
        this.consents = consents;
        this.weather = weather;
        this.places = places;
        this.idempotency = idempotency;
        this.demoOwner = demoOwner;
    }

    /**
     * Builds the travel context for a trip: one weather forecast per rule date (each call
     * independent, successful forecasts retained), derived packing items, and accommodations
     * only for PREVIOUS_DAY trips with a confirmed route of at least
     * {@value #ACCOMMODATION_MIN_DISTANCE} meters. A PROPOSED consent row is inserted when
     * the trip has no consent yet; an existing ACCEPTED/DECLINED status is never overwritten
     * by this operation. Nothing else is mutated: no trips, reminders, outboxes, or routes.
     */
    @Transactional
    public TravelContextResult context(UUID tripId, DepartureTiming departureTiming, RecommendationSort sort) {
        Trip trip = owned(tripId);
        if (departureTiming == null) throw badRequest("departureTiming is required");
        if (sort == null) throw badRequest("sort is required");

        List<LocalDate> dates = TravelRecommendationRules.weatherDates(trip.departureAt().toLocalDate(), departureTiming);
        List<WeatherForecast> forecasts = new ArrayList<>();
        List<ProviderFailure> failures = new ArrayList<>();
        for (LocalDate date : dates) {
            ProviderOutcome<WeatherForecast> outcome = weather.forecast(trip.destination(), date);
            if (outcome.success()) {
                forecasts.add(outcome.value());
            } else {
                failures.add(new ProviderFailure("WEATHER", date.toString(), outcome.kind()));
            }
        }

        List<PlaceCandidate> accommodations = new ArrayList<>();
        if (departureTiming == DepartureTiming.PREVIOUS_DAY) {
            PrivateCarRoute route = routes.findByTrip(tripId).orElse(null);
            if (route != null && route.distanceMeters() >= ACCOMMODATION_MIN_DISTANCE) {
                PlaceSearchRequest request = new PlaceSearchRequest(trip.destination(), PlaceCategory.ACCOMMODATION);
                ProviderOutcome<List<PlaceCandidate>> outcome = places.search(request);
                if (outcome.success()) {
                    accommodations.addAll(TravelRecommendationRules.sortPlaces(outcome.value(), sort));
                } else {
                    failures.add(new ProviderFailure("PLACE", PlaceCategory.ACCOMMODATION.name(), outcome.kind()));
                }
            }
        }

        consents.insertProposedIfAbsent(tripId, demoOwner.ownerId());
        FollowUpConsent consent = consents.find(tripId, demoOwner.ownerId()).orElseThrow();
        return new TravelContextResult(tripId, departureTiming, sort, forecasts,
                TravelRecommendationRules.packingItems(forecasts), accommodations, failures, consent.status());
    }

    /**
     * Records an ACCEPTED or DECLINED follow-up consent decision. The decision is idempotent
     * in the shared idempotency scope for the trip and owner: replaying the same key and
     * payload returns the stored decision, while the same key with a different payload is
     * rejected with 409. The consent row must already exist; a fresh idempotency key
     * intentionally records the new decision, so it can change ACCEPTED to DECLINED or
     * vice versa.
     */
    @Transactional
    public FollowUpConsent recordConsent(UUID tripId, boolean accepted, String key) {
        String owner = demoOwner.ownerId();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key must be nonblank");
        }
        if (key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key must be at most 200 characters");
        }
        Trip trip = owned(tripId);
        ConsentStatus target = accepted ? ConsentStatus.ACCEPTED : ConsentStatus.DECLINED;
        return idempotency.execute("travel-consent:" + tripId + ":" + owner, key,
                new Object[]{target}, FollowUpConsent.class, () -> {
            FollowUpConsent current = consents.find(tripId, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Follow-up consent must be proposed before a decision is recorded"));
            return consents.setDecision(tripId, owner, target, current.version())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Optimistic lock conflict on follow-up consent"));
        });
    }

    /**
     * Produces the accepted follow-up recommendation. With PROPOSED or DECLINED consent the
     * result short-circuits with empty lists and failures before any provider call; with
     * ACCEPTED consent, restaurants and attractions are searched independently, partial
     * success is preserved, each list is sorted and limited, and failures are typed.
     */
    @Transactional(readOnly = true)
    public PostTripRecommendationResult recommend(UUID tripId, RecommendationSort sort) {
        Trip trip = owned(tripId);
        if (sort == null) throw badRequest("sort is required");
        String owner = demoOwner.ownerId();
        FollowUpConsent consent = consents.find(tripId, owner).orElse(null);
        ConsentStatus status = consent == null ? ConsentStatus.PROPOSED : consent.status();

        List<PlaceCandidate> restaurants = new ArrayList<>();
        List<PlaceCandidate> attractions = new ArrayList<>();
        List<ProviderFailure> failures = new ArrayList<>();
        if (status == ConsentStatus.ACCEPTED) {
            search(trip, PlaceCategory.RESTAURANT, sort, restaurants, failures);
            search(trip, PlaceCategory.ATTRACTION, sort, attractions, failures);
        }
        return new PostTripRecommendationResult(tripId, sort, status,
                TravelRecommendationRules.sortPlaces(restaurants, sort),
                TravelRecommendationRules.sortPlaces(attractions, sort), failures);
    }

    private void search(Trip trip, PlaceCategory category, RecommendationSort sort,
                        List<PlaceCandidate> into, List<ProviderFailure> failures) {
        PlaceSearchRequest request = new PlaceSearchRequest(trip.destination(), category);
        ProviderOutcome<List<PlaceCandidate>> outcome = places.search(request);
        if (outcome.success()) {
            into.addAll(outcome.value());
        } else {
            failures.add(new ProviderFailure("PLACE", category.name(), outcome.kind()));
        }
    }

    private Trip owned(UUID tripId) {
        return trips.findByIdForOwner(tripId, demoOwner.ownerId()).orElseThrow(() -> notFound());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
}
