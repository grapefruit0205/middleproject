package com.middleproject.reminder.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * A confirmed private-car route proposal. The stable ID is derived only from route content
 * (origin, destination, departure, coordinates, durations, toll, provider, source), so two
 * fetches of the same route at different times compare equal while their fetchedAt/expiresAt
 * provenance differ. The recommended departure is the requested departure minus the traffic
 * duration.
 */
public record PrivateCarRoute(String stableId, String origin, String destination, OffsetDateTime departureAt,
                              GeoPoint originPoint, GeoPoint destinationPoint, int distanceMeters,
                              int baseDurationMinutes, int trafficDurationMinutes, int tollAmount,
                              String provider, String source, OffsetDateTime fetchedAt, OffsetDateTime expiresAt) {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    public PrivateCarRoute {
        if (fetchedAt == null) throw new IllegalArgumentException("fetchedAt is required");
        if (expiresAt == null || !expiresAt.isAfter(fetchedAt)) {
            throw new IllegalArgumentException("expiresAt must be after fetchedAt");
        }
    }

    public static PrivateCarRoute create(String origin, String destination, OffsetDateTime departureAt,
                                         GeoPoint originPoint, GeoPoint destinationPoint, int distanceMeters,
                                         int baseDurationMinutes, int trafficDurationMinutes, int tollAmount,
                                         String provider, String source, OffsetDateTime fetchedAt, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        RoutePlan plan = new RoutePlan(originPoint, destinationPoint, distanceMeters, baseDurationMinutes,
                trafficDurationMinutes, tollAmount, provider, source);
        String stableId = computeStableId(origin, destination, departureAt, plan);
        return new PrivateCarRoute(stableId, origin, destination, departureAt, plan.originPoint(),
                plan.destinationPoint(), plan.distanceMeters(), plan.baseDurationMinutes(),
                plan.trafficDurationMinutes(), plan.tollAmount(), plan.provider(), plan.source(),
                fetchedAt, fetchedAt.plus(ttl));
    }

    public static PrivateCarRoute fromPlan(String origin, String destination, OffsetDateTime departureAt,
                                           RoutePlan plan, OffsetDateTime fetchedAt, Duration ttl) {
        return create(origin, destination, departureAt, plan.originPoint(), plan.destinationPoint(),
                plan.distanceMeters(), plan.baseDurationMinutes(), plan.trafficDurationMinutes(),
                plan.tollAmount(), plan.provider(), plan.source(), fetchedAt, ttl);
    }

    public OffsetDateTime recommendedDepartureAt() {
        return departureAt.minusMinutes(trafficDurationMinutes);
    }

    /** True when the client-reported preview is no longer fresh at the given instant. */
    public static boolean stale(OffsetDateTime previewFetchedAt, Duration serverTtl, OffsetDateTime now) {
        return now == null || previewFetchedAt == null || serverTtl == null || serverTtl.isNegative()
                || !now.isBefore(previewFetchedAt.plus(serverTtl));
    }

    /** Stable ID derived only from route content; fetchedAt/expiresAt must not influence it. */
    public static String computeStableId(String origin, String destination, OffsetDateTime departureAt, RoutePlan plan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String content = origin + "\n" + destination + "\n" + departureAt + "\n"
                    + plan.originPoint() + "\n" + plan.destinationPoint() + "\n" + plan.distanceMeters() + "\n"
                    + plan.baseDurationMinutes() + "\n" + plan.trafficDurationMinutes() + "\n" + plan.tollAmount()
                    + "\n" + plan.provider() + "\n" + plan.source();
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute proposal id", e);
        }
    }
}
