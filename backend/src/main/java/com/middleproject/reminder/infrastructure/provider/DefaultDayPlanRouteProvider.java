package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.domain.DayPlanRouteEstimate;
import com.middleproject.reminder.domain.DayPlanRouteRequest;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.port.DayPlanRouteProvider;
import com.middleproject.reminder.port.RouteProviderPort;
import com.middleproject.reminder.transport.domain.PublicTransitRoutePreview;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.PublicTransitRoutePort;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges the day-plan preview to existing road and Kakao public-transit ports. Public
 * transport remains fail-closed when the optional transport integration is disabled; train and
 * air booking are intentionally not fabricated by this provider.
 */
@Component
public class DefaultDayPlanRouteProvider implements DayPlanRouteProvider {
    private final RouteProviderPort road;
    private final Optional<PublicTransitRoutePort> publicTransit;

    public DefaultDayPlanRouteProvider(RouteProviderPort road, Optional<PublicTransitRoutePort> publicTransit) {
        this.road = road;
        this.publicTransit = publicTransit;
    }

    @Override
    public ProviderOutcome<DayPlanRouteEstimate> estimate(DayPlanRouteRequest request) {
        String mode = request.mode();
        if ("CAR".equals(mode)) return estimateRoad(request);
        if ("SUBWAY".equals(mode) || "CITY_BUS".equals(mode)
                || "EXPRESS_BUS".equals(mode) || "INTERCITY_BUS".equals(mode)) {
            return estimatePublicTransit(request);
        }
        return new ProviderOutcome.Empty<>();
    }

    private ProviderOutcome<DayPlanRouteEstimate> estimateRoad(DayPlanRouteRequest request) {
        ProviderOutcome<RoutePlan> outcome = road.plan(new RoutePlanRequest(request.originName(), request.destinationName(),
                request.originCoordinates(), request.destinationCoordinates(), request.requestedArrivalAt()));
        if (!outcome.success()) return copy(outcome);
        RoutePlan route = outcome.value();
        return new ProviderOutcome.Success<>(new DayPlanRouteEstimate(route.trafficDurationMinutes(), route.provider(), route.source(), null));
    }

    private ProviderOutcome<DayPlanRouteEstimate> estimatePublicTransit(DayPlanRouteRequest request) {
        if (publicTransit.isEmpty()) return new ProviderOutcome.Empty<>();
        LandmarkCandidate origin = new LandmarkCandidate(request.originName(), "", request.originCoordinates().latitude(), request.originCoordinates().longitude());
        LandmarkCandidate destination = new LandmarkCandidate(request.destinationName(), "", request.destinationCoordinates().latitude(), request.destinationCoordinates().longitude());
        TransportOutcome<PublicTransitRoutePreview> outcome = publicTransit.get().preview(origin, destination);
        if (outcome.isSuccess()) {
            Integer duration = outcome.value().estimatedDurationMinutes();
            if (duration == null) return new ProviderOutcome.Empty<>();
            return new ProviderOutcome.Success<>(new DayPlanRouteEstimate(duration, "kakao-map", "public-transit", outcome.value().kakaoMapUrl()));
        }
        if (outcome.isEmpty()) return new ProviderOutcome.Empty<>();
        return switch (outcome.failureKind()) {
            case TIMEOUT -> new ProviderOutcome.Timeout<>();
            case RATE_LIMITED -> new ProviderOutcome.RateLimited<>();
            case AUTH_REJECTED, DISABLED_INSECURE -> new ProviderOutcome.Empty<>();
            case MALFORMED -> new ProviderOutcome.Malformed<>(outcome.errorMessage() == null ? "public transit route provider failed" : outcome.errorMessage());
        };
    }

    private static <T> ProviderOutcome<DayPlanRouteEstimate> copy(ProviderOutcome<T> outcome) {
        return switch (outcome.kind()) {
            case TIMEOUT -> new ProviderOutcome.Timeout<>();
            case RATE_LIMITED -> new ProviderOutcome.RateLimited<>();
            case EMPTY -> new ProviderOutcome.Empty<>();
            case MALFORMED -> new ProviderOutcome.Malformed<>("route provider returned malformed data");
            case SUCCESS -> throw new IllegalStateException("success must be handled before copy");
        };
    }
}
