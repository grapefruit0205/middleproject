package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;

/**
 * Typed port for route planning. Adapters must return a {@link ProviderOutcome} instead of
 * throwing for provider-side failures.
 */
public interface RouteProviderPort {
    ProviderOutcome<RoutePlan> plan(RoutePlanRequest request);
}
