package com.middleproject.reminder;

import com.middleproject.reminder.application.ProviderCallPolicy;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RoutePlan;
import com.middleproject.reminder.domain.RoutePlanRequest;
import com.middleproject.reminder.support.FakeGeocodingPort;
import com.middleproject.reminder.support.FakeRouteProviderPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateCarPlanningTest {

    private static final OffsetDateTime DEPART = OffsetDateTime.parse("2030-01-01T10:00:00+09:00");
    private static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);
    private static final GeoPoint BUSAN = new GeoPoint(35.1796, 129.0756);

    private PrivateCarRoute routeAt(OffsetDateTime fetchedAt, Duration ttl) {
        return PrivateCarRoute.create("Seoul", "Busan", DEPART, SEOUL, BUSAN, 32_400, 60, 90, 8_500,
                "fake-route", "fake", fetchedAt, ttl);
    }

    @Test
    void missingQuestionFollowsStableOrder() {
        assertEquals("origin", PrivateCarPlanningInput.of(null, "Busan", DEPART, 30).missingQuestion());
        assertEquals("destination", PrivateCarPlanningInput.of("Seoul", null, DEPART, 30).missingQuestion());
        assertEquals("departureAt", PrivateCarPlanningInput.of("Seoul", "Busan", null, 30).missingQuestion());
        assertEquals("private_car.reminder_lead_minutes",
                PrivateCarPlanningInput.of("Seoul", "Busan", DEPART, null).missingQuestion());
        assertNull(PrivateCarPlanningInput.of("Seoul", "Busan", DEPART, 30).missingQuestion());
        assertThrows(IllegalArgumentException.class, () -> PrivateCarPlanningInput.of("Seoul", "Busan", DEPART, -1));
        assertThrows(IllegalArgumentException.class, () -> PrivateCarPlanningInput.of("Seoul", "Busan", DEPART, 1441));
    }

    @Test
    void fakeProvidersAreDeterministicAndReturnTypedOutcomes() {
        FakeGeocodingPort geocoding = new FakeGeocodingPort();
        ProviderOutcome<GeoPoint> first = geocoding.geocode("Seoul");
        ProviderOutcome<GeoPoint> second = geocoding.geocode("Seoul");
        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(SEOUL, first.value());
        assertEquals(SEOUL, second.value());

        FakeRouteProviderPort routes = new FakeRouteProviderPort();
        RoutePlanRequest request = new RoutePlanRequest("Seoul", "Busan", SEOUL, BUSAN, DEPART);
        ProviderOutcome<RoutePlan> plan = routes.plan(request);
        ProviderOutcome<RoutePlan> replay = routes.plan(request);
        assertTrue(plan.success());
        assertEquals(plan.value(), replay.value());
        assertEquals(32_400, plan.value().distanceMeters());
        assertEquals(60, plan.value().baseDurationMinutes());
        assertEquals(90, plan.value().trafficDurationMinutes());
        assertEquals(8_500, plan.value().tollAmount());
        assertEquals("fake-route", plan.value().provider());
    }

    @Test
    void providerFailuresAreTyped() {
        FakeRouteProviderPort routes = new FakeRouteProviderPort();
        RoutePlanRequest request = new RoutePlanRequest("Seoul", "Busan", SEOUL, BUSAN, DEPART);
        routes.failWith(new ProviderOutcome.Timeout<>());
        ProviderOutcome<RoutePlan> timeout = routes.plan(request);
        assertEquals(ProviderOutcome.Kind.TIMEOUT, timeout.kind());
        assertTrue(timeout.retryable());
        routes.failWith(new ProviderOutcome.RateLimited<>());
        ProviderOutcome<RoutePlan> limited = routes.plan(request);
        assertEquals(ProviderOutcome.Kind.RATE_LIMITED, limited.kind());
        assertTrue(limited.retryable());
        routes.failWith(new ProviderOutcome.Empty<>());
        assertEquals(ProviderOutcome.Kind.EMPTY, routes.plan(request).kind());
        assertFalse(routes.plan(request).retryable());
        routes.failWith(new ProviderOutcome.Malformed<>("missing distance"));
        ProviderOutcome<RoutePlan> malformed = routes.plan(request);
        assertEquals(ProviderOutcome.Kind.MALFORMED, malformed.kind());
        assertFalse(malformed.retryable());
    }

    @Test
    void recommendedDepartureAccountsForTraffic() {
        PrivateCarRoute route = routeAt(OffsetDateTime.parse("2030-01-01T09:00:00+09:00"), Duration.ofMinutes(10));
        assertEquals(OffsetDateTime.parse("2030-01-01T08:30:00+09:00"), route.recommendedDepartureAt());
        assertEquals(90, route.trafficDurationMinutes());
        assertEquals(60, route.baseDurationMinutes());
    }

    @Test
    void routeCarriesProvenanceAndStableIdExcludesTiming() {
        OffsetDateTime fetchedA = OffsetDateTime.parse("2030-01-01T09:00:00+09:00");
        OffsetDateTime fetchedB = OffsetDateTime.parse("2030-01-01T09:05:00+09:00");
        PrivateCarRoute a = routeAt(fetchedA, Duration.ofMinutes(10));
        PrivateCarRoute b = routeAt(fetchedB, Duration.ofMinutes(20));
        assertEquals(a.stableId(), b.stableId());
        assertNotEquals(a.fetchedAt(), b.fetchedAt());
        assertNotEquals(a.expiresAt(), b.expiresAt());
        assertEquals(a.expiresAt(), a.fetchedAt().plusMinutes(10));
        assertEquals(b.expiresAt(), b.fetchedAt().plusMinutes(20));
        assertTrue(b.expiresAt().isAfter(b.fetchedAt()));
        assertEquals(SEOUL, b.originPoint());
        assertEquals(BUSAN, b.destinationPoint());
        assertEquals("fake", b.source());
        PrivateCarRoute tolled = PrivateCarRoute.create("Seoul", "Busan", DEPART, SEOUL, BUSAN, 32_400, 60, 90,
                9_000, "fake-route", "fake", fetchedA, Duration.ofMinutes(10));
        assertNotEquals(a.stableId(), tolled.stableId());
    }

    @Test
    void providerCallPolicyConstantsAndExponentialBackoff() {
        assertEquals(Duration.ofSeconds(2), ProviderCallPolicy.DEFAULT_CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(5), ProviderCallPolicy.DEFAULT_RESPONSE_TIMEOUT);
        assertEquals(1, ProviderCallPolicy.DEFAULT_MAX_RETRIES);
        ProviderCallPolicy policy = new ProviderCallPolicy();
        assertEquals(2, policy.connectTimeout().getSeconds());
        assertEquals(5, policy.responseTimeout().getSeconds());
        assertEquals(1, policy.maxRetries());
        assertEquals(1000, policy.backoffMillis(1));
        assertEquals(2000, policy.backoffMillis(2));
        assertEquals(4000, policy.backoffMillis(3));
        assertThrows(IllegalArgumentException.class, () -> policy.backoffMillis(0));
    }

    @Test
    void fakesServeQueuedOutcomesInOrderThenSucceed() {
        FakeRouteProviderPort routes = new FakeRouteProviderPort();
        RoutePlanRequest request = new RoutePlanRequest("Seoul", "Busan", SEOUL, BUSAN, DEPART);
        routes.queue(new ProviderOutcome.Timeout<>(), new ProviderOutcome.RateLimited<>());
        assertEquals(ProviderOutcome.Kind.TIMEOUT, routes.plan(request).kind());
        assertEquals(ProviderOutcome.Kind.RATE_LIMITED, routes.plan(request).kind());
        assertTrue(routes.plan(request).success());
        assertEquals(3, routes.callCount());

        FakeGeocodingPort geocoding = new FakeGeocodingPort();
        geocoding.queue(new ProviderOutcome.Empty<>());
        assertEquals(ProviderOutcome.Kind.EMPTY, geocoding.geocode("Seoul").kind());
        assertEquals(ProviderOutcome.Kind.SUCCESS, geocoding.geocode("Seoul").kind());
        assertEquals(2, geocoding.callCount());
    }
}
