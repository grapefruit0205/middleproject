package com.middleproject.reminder.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable domain rules for travel recommendations: which weather dates matter,
 * which packing items follow from forecast conditions, and how place candidates
 * are sorted and limited. All methods are pure; input lists are never mutated.
 */
public final class TravelRecommendationRules {

    public static final int MAX_RECOMMENDATIONS = 5;

    private TravelRecommendationRules() {
    }

    /** Returns the weather dates to fetch, in ascending order, for the given departure timing. */
    public static List<LocalDate> weatherDates(LocalDate departure, DepartureTiming timing) {
        if (departure == null) throw new IllegalArgumentException("departure is required");
        if (timing == null) throw new IllegalArgumentException("timing is required");
        return switch (timing) {
            case SAME_DAY -> List.of(departure);
            case PREVIOUS_DAY -> List.of(departure.minusDays(1), departure);
        };
    }

    /**
     * Returns the distinct packing items implied by the given forecasts, in a stable
     * (non-condition-dependent) order.
     */
    public static List<String> packingItems(List<WeatherForecast> forecasts) {
        if (forecasts == null) throw new IllegalArgumentException("forecasts is required");
        Set<String> items = new LinkedHashSet<>();
        for (WeatherForecast forecast : forecasts) {
            if (forecast == null) continue;
            switch (forecast.condition()) {
                case RAIN -> {
                    items.add("umbrella");
                    items.add("waterproof footwear");
                }
                case SNOW, FREEZING -> items.add("warm outerwear");
                case HOT -> items.add("hydration");
            }
        }
        return List.copyOf(items);
    }

    /**
     * Returns up to {@value #MAX_RECOMMENDATIONS} places ordered by the given criterion,
     * without mutating the input. Ties break by name, locale-independent.
     */
    public static List<PlaceCandidate> sortPlaces(List<PlaceCandidate> places, RecommendationSort sort) {
        if (places == null) throw new IllegalArgumentException("places is required");
        if (sort == null) throw new IllegalArgumentException("sort is required");
        List<PlaceCandidate> copy = new ArrayList<>(places);
        Comparator<PlaceCandidate> comparator = switch (sort) {
            case DISTANCE -> Comparator.comparingInt(PlaceCandidate::distanceMeters);
            case PRICE -> Comparator.comparing(PlaceCandidate::price, Comparator.nullsLast(Double::compareTo));
            case RATING -> Comparator.comparing(PlaceCandidate::rating, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        copy.sort(comparator.thenComparing(PlaceCandidate::name));
        return List.copyOf(copy.subList(0, Math.min(copy.size(), MAX_RECOMMENDATIONS)));
    }
}
