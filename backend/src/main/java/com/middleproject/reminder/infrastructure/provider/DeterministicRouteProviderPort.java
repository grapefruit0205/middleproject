package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.port.RouteProviderPort;
import org.springframework.stereotype.Component;

/**
 * Deterministic in-memory route adapter so the normal Spring Boot context starts without
 * network access or credentials. Tests override this bean with a {@code @Primary} fake.
 */
@Component
public class DeterministicRouteProviderPort implements RouteProviderPort {

    @Override
    public ProviderOutcome<RoutePlan> plan(RoutePlanRequest request) {
        GeoPoint origin = request.originPoint() == null ? new GeoPoint(37.5665, 126.9780) : request.originPoint();
        GeoPoint destination = request.destinationPoint() == null ? new GeoPoint(35.1796, 129.0756) : request.destinationPoint();
        return new ProviderOutcome.Success<>(new RoutePlan(origin, destination,
                32_400, 60, 90, 8_500, "demo-route", "demo"));
    }
}
