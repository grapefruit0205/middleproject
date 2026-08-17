package com.middleproject.reminder.transport.domain;

/** Read-only route estimate. The Kakao Map URL is an official handoff, never a booking confirmation. */
public record PublicTransitRoutePreview(
        LandmarkCandidate origin,
        LandmarkCandidate destination,
        String routeType,
        Integer estimatedDurationMinutes,
        Integer transferCount,
        Integer fareKrw,
        String kakaoMapUrl
) {
    public PublicTransitRoutePreview {
        if (origin == null || destination == null) throw new IllegalArgumentException("route endpoints are required");
        if (estimatedDurationMinutes != null && estimatedDurationMinutes < 0) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (transferCount != null && transferCount < 0) throw new IllegalArgumentException("transfers must not be negative");
        if (fareKrw != null && fareKrw < 0) throw new IllegalArgumentException("fare must not be negative");
    }
}
