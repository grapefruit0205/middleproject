package com.middleproject.reminder.support;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.port.RouteProviderPort;

import java.util.ArrayList;
import java.util.List;

/** Deterministic in-memory route provider for tests. */
public class FakeRouteProviderPort implements RouteProviderPort {
    public static final int DISTANCE_METERS = 32_400;
    public static final int BASE_DURATION = 60;
    public static final int TRAFFIC_DURATION = 90;
    public static final int TOLL = 8_500;

    private ProviderOutcome<RoutePlan> fixed;
    private final List<ProviderOutcome<RoutePlan>> queue = new ArrayList<>();
    private final List<RoutePlanRequest> calls = new ArrayList<>();

    public FakeRouteProviderPort failWith(ProviderOutcome<RoutePlan> outcome) { this.fixed = outcome; return this; }

    /** Outcomes are served in order; once the queue is empty the provider succeeds again. */
    public FakeRouteProviderPort queue(ProviderOutcome<RoutePlan>... outcomes) {
        for (ProviderOutcome<RoutePlan> outcome : outcomes) queue.add(outcome);
        return this;
    }

    public void reset() { fixed = null; queue.clear(); calls.clear(); }
    public List<RoutePlanRequest> calls() { return List.copyOf(calls); }
    public int callCount() { return calls.size(); }

    @Override
    public ProviderOutcome<RoutePlan> plan(RoutePlanRequest request) {
        calls.add(request);
        if (fixed != null) return fixed;
        if (!queue.isEmpty()) return queue.remove(0);
        return new ProviderOutcome.Success<>(new RoutePlan(request.originPoint(), request.destinationPoint(),
                DISTANCE_METERS, BASE_DURATION, TRAFFIC_DURATION, TOLL, "fake-route", "fake"));
    }
}
