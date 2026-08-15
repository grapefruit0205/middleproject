package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.application.ProviderCallPolicy;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.port.GeocodingPort;
import com.middleproject.reminder.port.RouteProviderPort;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Retry wrapper around the deterministic provider adapters. Retryable outcomes
 * (TIMEOUT, RATE_LIMITED) are retried at most {@link ProviderCallPolicy#maxRetries()} times;
 * EMPTY and MALFORMED are never retried. There is no real HTTP call and no backoff sleep in
 * this phase: {@link ProviderCallPolicy} defines the production contract (timeouts, retry
 * count, exponential backoff) that the real provider adapters will implement. Call counts
 * are verified through the fake port counters in tests.
 */
@Component
public class ProviderCallPolicyClient {

    private final ProviderCallPolicy policy;
    private final GeocodingPort geocoding;
    private final RouteProviderPort routeProvider;

    public ProviderCallPolicyClient(ProviderCallPolicy policy, GeocodingPort geocoding,
                                    RouteProviderPort routeProvider) {
        this.policy = policy;
        this.geocoding = geocoding;
        this.routeProvider = routeProvider;
    }

    public ProviderOutcome<GeoPoint> geocode(String location) {
        ProviderOutcome<GeoPoint> outcome = geocoding.geocode(location);
        return retryable(outcome) ? retryOnce(() -> geocoding.geocode(location)) : outcome;
    }

    public ProviderOutcome<RoutePlan> plan(RoutePlanRequest request) {
        ProviderOutcome<RoutePlan> outcome = routeProvider.plan(request);
        return retryable(outcome) ? retryOnce(() -> routeProvider.plan(request)) : outcome;
    }

    private <T> ProviderOutcome<T> retryOnce(Supplier<ProviderOutcome<T>> call) {
        return call.get();
    }

    private boolean retryable(ProviderOutcome<?> outcome) {
        return outcome.retryable() && policy.maxRetries() > 0;
    }
}
