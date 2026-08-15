package com.middleproject.reminder.domain;

import java.util.List;
import java.util.UUID;

public record TravelContextResult(
        UUID tripId,
        DepartureTiming departureTiming,
        RecommendationSort sort,
        List<WeatherForecast> forecasts,
        List<String> packingItems,
        List<PlaceCandidate> accommodations,
        List<ProviderFailure> failures,
        ConsentStatus consentStatus) {

    public TravelContextResult {
        if (tripId == null) {
            throw new IllegalArgumentException("tripId must not be null");
        }
        if (departureTiming == null) {
            throw new IllegalArgumentException("departureTiming must not be null");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort must not be null");
        }
        if (consentStatus == null) {
            throw new IllegalArgumentException("consentStatus must not be null");
        }
        forecasts = List.copyOf(forecasts);
        packingItems = List.copyOf(packingItems);
        accommodations = List.copyOf(accommodations);
        failures = List.copyOf(failures);
    }
}
