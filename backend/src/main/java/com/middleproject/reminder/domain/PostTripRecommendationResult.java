package com.middleproject.reminder.domain;

import java.util.List;
import java.util.UUID;

public record PostTripRecommendationResult(
        UUID tripId,
        RecommendationSort sort,
        ConsentStatus consentStatus,
        List<PlaceCandidate> restaurants,
        List<PlaceCandidate> attractions,
        List<ProviderFailure> failures) {

    public PostTripRecommendationResult {
        if (tripId == null) {
            throw new IllegalArgumentException("tripId must not be null");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort must not be null");
        }
        if (consentStatus == null) {
            throw new IllegalArgumentException("consentStatus must not be null");
        }
        restaurants = List.copyOf(restaurants);
        attractions = List.copyOf(attractions);
        failures = List.copyOf(failures);
    }
}
