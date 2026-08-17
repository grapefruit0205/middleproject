package com.middleproject.reminder.application;

import com.middleproject.reminder.transport.domain.BusRoute;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.NearbyBusStop;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.LandmarkSearchPort;
import com.middleproject.reminder.transport.port.PublicTransportPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LandmarkBusStopDiscoveryService {
    private final LandmarkSearchPort landmarks;
    private final PublicTransportPort transport;

    @Autowired
    public LandmarkBusStopDiscoveryService(@Autowired(required = false) LandmarkSearchPort landmarks,
                                           @Autowired(required = false) PublicTransportPort transport) {
        this.landmarks = landmarks;
        this.transport = transport;
    }

    public TransportOutcome<DiscoveryResult> find(String query, int maxCandidates) {
        if (query == null || query.isBlank() || query.length() > 200) {
            return TransportOutcome.malformed("landmark must be nonblank and at most 200 characters");
        }
        if (landmarks == null || transport == null) {
            return TransportOutcome.disabledInsecure("Landmark bus-stop discovery is disabled");
        }
        int limit = Math.max(1, Math.min(maxCandidates, 3));
        TransportOutcome<List<LandmarkCandidate>> placeOutcome = landmarks.search(query.trim(), 3);
        if (!placeOutcome.isSuccess()) return copyFailure(placeOutcome);

        List<LandmarkCandidate> places = placeOutcome.value();
        Map<String, BaseStopCandidate> unique = new LinkedHashMap<>();
        for (LandmarkCandidate place : places) {
            TransportOutcome<List<NearbyBusStop>> stopOutcome =
                    transport.getNearbyBusStops(place.latitude(), place.longitude(), 1, 20);
            if (!stopOutcome.isSuccess()) continue;
            for (NearbyBusStop stop : stopOutcome.value()) {
                int distance = distanceMeters(place.latitude(), place.longitude(), stop.latitude(), stop.longitude());
                BaseStopCandidate candidate = new BaseStopCandidate(
                        place.name(), place.address(), stop.nodeId(), stop.nodeName(), stop.nodeNo(),
                        stop.cityCode(), distance);
                unique.merge(stop.cityCode() + ":" + stop.nodeId(), candidate,
                        (left, right) -> left.distanceMeters() <= right.distanceMeters() ? left : right);
            }
        }
        List<StopCandidate> candidates = unique.values().stream()
                .sorted(Comparator.comparingInt(BaseStopCandidate::distanceMeters))
                .limit(limit)
                .map(candidate -> {
                    TransportOutcome<List<BusRoute>> routes = transport.getRoutesThroughStop(
                            candidate.cityCode(), candidate.nodeId(), 1, 20);
                    return candidate.withRoutes(routes.isSuccess()
                            ? routes.value().stream().map(RouteSummary::from).toList()
                            : List.of());
                })
                .toList();
        if (candidates.isEmpty()) return TransportOutcome.empty();
        boolean selectionRequired = hasGeographicallyDistinctPlaces(places) || candidates.size() > 1
                && candidates.get(1).distanceMeters() - candidates.get(0).distanceMeters() < 80;
        return TransportOutcome.success(new DiscoveryResult(query.trim(), places, candidates, selectionRequired));
    }

    private static <T> TransportOutcome<T> copyFailure(TransportOutcome<?> outcome) {
        if (outcome.isEmpty()) return TransportOutcome.empty();
        return switch (outcome.failureKind()) {
            case AUTH_REJECTED -> TransportOutcome.authRejected(outcome.errorMessage());
            case RATE_LIMITED -> TransportOutcome.rateLimited(outcome.errorMessage());
            case TIMEOUT -> TransportOutcome.timeout(outcome.errorMessage());
            case DISABLED_INSECURE -> TransportOutcome.disabledInsecure(outcome.errorMessage());
            case MALFORMED -> TransportOutcome.malformed(outcome.errorMessage());
        };
    }

    private static int distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earth = 6_371_000.0;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return (int) Math.round(earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static boolean hasGeographicallyDistinctPlaces(List<LandmarkCandidate> places) {
        if (places.size() < 2) return false;
        LandmarkCandidate first = places.getFirst();
        return places.stream().skip(1).anyMatch(place ->
                distanceMeters(first.latitude(), first.longitude(), place.latitude(), place.longitude()) > 2_000);
    }

    public record DiscoveryResult(String query, List<LandmarkCandidate> places,
                                  List<StopCandidate> candidates, boolean selectionRequired) {}

    public record StopCandidate(String landmarkName, String landmarkAddress, String nodeId,
                                String stopName, String stopNumber, int cityCode,
                                int distanceMeters, List<RouteSummary> routes) {}

    private record BaseStopCandidate(String landmarkName, String landmarkAddress, String nodeId,
                                     String stopName, String stopNumber, int cityCode, int distanceMeters) {
        StopCandidate withRoutes(List<RouteSummary> routes) {
            return new StopCandidate(landmarkName, landmarkAddress, nodeId, stopName, stopNumber,
                    cityCode, distanceMeters, routes);
        }
    }

    public record RouteSummary(String routeId, String routeNo, String routeType, String direction) {
        static RouteSummary from(BusRoute route) {
            return new RouteSummary(route.routeId(), route.routeNo(), route.routeType(),
                    route.startNodeName() + " → " + route.endNodeName());
        }
    }
}
