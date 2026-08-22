package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.DayPlanRouteEstimate;
import com.middleproject.reminder.domain.DayPlanRouteRequest;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.port.DayPlanRouteProvider;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a read-only route/timeline preview for a validated draft. Place resolution and route
 * providers are called only when coordinates have not already been explicitly confirmed. No
 * day-plan, reminder, notification, or scheduler state is written here.
 */
@Service
public class DayPlanRoutePreviewService {
    public static final int DEFAULT_BUFFER_MINUTES = 10;

    private final DayPlanValidationService validation;
    private final PlaceDiscoveryService places;
    private final DayPlanRouteProvider routes;
    private final Clock clock;

    public DayPlanRoutePreviewService(DayPlanValidationService validation, PlaceDiscoveryService places,
                                      DayPlanRouteProvider routes, Clock clock) {
        this.validation = validation;
        this.places = places;
        this.routes = routes;
        this.clock = clock;
    }

    public PreviewResult preview(DayPlanDraft draft) {
        DayPlanValidationService.ValidationResult checked = validation.validate(draft);
        Instant fetchedAt = Instant.now(clock);
        if (!checked.valid()) {
            return new PreviewResult(PreviewStatus.INVALID_DRAFT, List.of(), List.of(), List.of(),
                    issues(checked.issues()), fetchedAt);
        }

        Resolution origin = resolve("originName", draft.originName(), draft.originAddress(), draft.originCoordinates());
        if (origin.selection() != null) {
            return selectionResult(origin.selection(), fetchedAt);
        }
        if (origin.issue() != null) return failure(PreviewStatus.PLACE_RESOLUTION_FAILED, origin.issue(), fetchedAt);

        List<ResolvedPlace> resolved = new ArrayList<>();
        resolved.add(origin.place());
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem item = draft.items().get(i);
            Resolution destination = resolve("items[" + i + "].placeName", item.placeName(), item.address(), item.coordinates());
            if (destination.selection() != null) {
                return selectionResult(destination.selection(), fetchedAt);
            }
            if (destination.issue() != null) {
                return failure(PreviewStatus.PLACE_RESOLUTION_FAILED, destination.issue(), fetchedAt);
            }
            resolved.add(destination.place());
        }

        List<TravelLegPreview> legs = new ArrayList<>();
        List<PreviewIssue> timelineIssues = new ArrayList<>();
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem destinationItem = draft.items().get(i);
            OffsetDateTime targetArrival = destinationItem.startsAt();
            if (targetArrival == null) {
                // A flexible item is still a valid itinerary entry. Its unanchored travel
                // estimate must not be fabricated or scheduled until the user supplies a time.
                continue;
            }
            int fromResolvedIndex = i;
            ResolvedPlace from = resolved.get(fromResolvedIndex);
            ResolvedPlace to = resolved.get(i + 1);
            OffsetDateTime previousAvailableAt = i == 0 ? null : endOf(draft.items().get(i - 1));
            ProviderOutcome<DayPlanRouteEstimate> estimate = routes.estimate(new DayPlanRouteRequest(
                    from.name(), from.coordinates(), to.name(), to.coordinates(), normalizeMode(destinationItem.travelMode()), targetArrival));
            if (!estimate.success()) {
                return new PreviewResult(PreviewStatus.ROUTE_UNAVAILABLE, resolved, List.of(), legs,
                        List.of(new PreviewIssue("ROUTE_UNAVAILABLE",
                                "route provider returned " + estimate.kind(), "items[" + i + "].travelMode")), fetchedAt);
            }
            DayPlanRouteEstimate value = estimate.value();
            OffsetDateTime arrivalAt = targetArrival.minusMinutes(DEFAULT_BUFFER_MINUTES);
            OffsetDateTime departureAt = arrivalAt.minusMinutes(value.durationMinutes());
            if (i > 0 && previousAvailableAt != null && departureAt.isBefore(previousAvailableAt)) {
                timelineIssues.add(new PreviewIssue("TRAVEL_WINDOW_CONFLICT",
                        "the route cannot fit between two fixed schedule items", "items[" + i + "]"));
            }
            legs.add(new TravelLegPreview(i == 0 ? -1 : i - 1, i, normalizeMode(destinationItem.travelMode()),
                    value.durationMinutes(), DEFAULT_BUFFER_MINUTES, departureAt, arrivalAt,
                    value.provider(), value.source(), value.handoffUrl()));
        }
        if (!timelineIssues.isEmpty()) {
            return new PreviewResult(PreviewStatus.INVALID_TIMELINE, resolved, List.of(), List.of(), timelineIssues, fetchedAt);
        }
        return new PreviewResult(PreviewStatus.READY, resolved, List.of(), legs, List.of(), fetchedAt);
    }

    private Resolution resolve(String path, String query, String address, GeoPoint coordinates) {
        if (coordinates != null) {
            return new Resolution(new ResolvedPlace(path, query, address == null ? "" : address, coordinates), null, null);
        }
        TransportOutcome<List<LandmarkCandidate>> outcome = places.resolve(query, 3);
        if (outcome.isSuccess()) {
            if (outcome.value().size() == 1) {
                LandmarkCandidate candidate = outcome.value().getFirst();
                return new Resolution(new ResolvedPlace(path, candidate.name(), candidate.address(),
                        new GeoPoint(candidate.latitude(), candidate.longitude())), null, null);
            }
            if (outcome.value().size() > 1) {
                return new Resolution(null, new PlaceSelection(path, outcome.value()), null);
            }
        }
        String reason = outcome.isEmpty() ? "no place candidate" : "place provider returned " + outcome.errorMessage();
        return new Resolution(null, null, new PreviewIssue("PLACE_RESOLUTION_FAILED", reason, path));
    }

    private OffsetDateTime endOf(ScheduleDraftItem item) {
        if (item.endsAt() != null) return item.endsAt();
        if (item.startsAt() != null && item.durationMinutes() != null && item.durationMinutes() >= 0) {
            return item.startsAt().plusMinutes(item.durationMinutes());
        }
        return null;
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "";
        return switch (mode.trim().toUpperCase()) {
            case "자차", "자동차", "CAR", "PRIVATE_CAR" -> "CAR";
            case "지하철", "SUBWAY" -> "SUBWAY";
            case "버스", "시내버스", "BUS", "CITY_BUS" -> "CITY_BUS";
            case "고속버스", "EXPRESS_BUS" -> "EXPRESS_BUS";
            case "시외버스", "INTERCITY_BUS" -> "INTERCITY_BUS";
            case "기차", "KTX", "TRAIN" -> "TRAIN";
            case "항공", "비행기", "AIR" -> "AIR";
            default -> mode.trim().toUpperCase();
        };
    }

    private PreviewResult selectionResult(PlaceSelection selection, Instant fetchedAt) {
        return new PreviewResult(PreviewStatus.PLACE_SELECTION_REQUIRED, List.of(), List.of(selection), List.of(), List.of(), fetchedAt);
    }

    private PreviewResult failure(PreviewStatus status, PreviewIssue issue, Instant fetchedAt) {
        return new PreviewResult(status, List.of(), List.of(), List.of(), List.of(issue), fetchedAt);
    }

    private List<PreviewIssue> issues(List<DayPlanValidationService.ValidationIssue> source) {
        return source.stream().map(issue -> new PreviewIssue(issue.code(), issue.message(), issue.path())).toList();
    }

    private record Resolution(ResolvedPlace place, PlaceSelection selection, PreviewIssue issue) {}

    public enum PreviewStatus {
        READY, INVALID_DRAFT, PLACE_SELECTION_REQUIRED, PLACE_RESOLUTION_FAILED, ROUTE_UNAVAILABLE, INVALID_TIMELINE
    }

    public record ResolvedPlace(String path, String name, String address, GeoPoint coordinates) {}

    public record PlaceSelection(String path, List<LandmarkCandidate> candidates) {
        public PlaceSelection {
            candidates = List.copyOf(candidates);
        }
    }

    public record TravelLegPreview(int fromItemIndex, int toItemIndex, String mode,
                                   int durationMinutes, int bufferMinutes,
                                   OffsetDateTime departureAt, OffsetDateTime arrivalAt,
                                   String provider, String source, String handoffUrl) {}

    public record PreviewIssue(String code, String message, String path) {}

    public record PreviewResult(PreviewStatus status, List<ResolvedPlace> resolvedPlaces,
                                List<PlaceSelection> placeSelections, List<TravelLegPreview> legs,
                                List<PreviewIssue> issues, Instant fetchedAt) {
        public PreviewResult {
            resolvedPlaces = List.copyOf(resolvedPlaces);
            placeSelections = List.copyOf(placeSelections);
            legs = List.copyOf(legs);
            issues = List.copyOf(issues);
        }
    }
}
