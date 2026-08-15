package com.middleproject.reminder;

import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceCategory;
import com.middleproject.reminder.domain.ProviderFailure;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.TravelRecommendationRules;
import com.middleproject.reminder.domain.WeatherCondition;
import com.middleproject.reminder.domain.WeatherForecast;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelRecommendationDomainTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-08-15T01:00:00Z");

    // ---------- weatherDates ----------

    @Test
    void weatherDatesSameDayReturnsOnlyDepartureDate() {
        LocalDate departure = LocalDate.of(2026, 8, 15);

        assertEquals(List.of(departure), TravelRecommendationRules.weatherDates(departure, DepartureTiming.SAME_DAY));
    }

    @Test
    void weatherDatesPreviousDayReturnsTwoDatesAscending() {
        LocalDate departure = LocalDate.of(2026, 8, 15);

        assertEquals(
            List.of(LocalDate.of(2026, 8, 14), departure),
            TravelRecommendationRules.weatherDates(departure, DepartureTiming.PREVIOUS_DAY)
        );
    }

    // ---------- packingItems ----------

    @Test
    void packingItemsMapsRainToUmbrellaAndWaterproofFootwear() {
        List<WeatherForecast> forecasts = List.of(forecast(WeatherCondition.RAIN));

        assertEquals(List.of("umbrella", "waterproof footwear"), TravelRecommendationRules.packingItems(forecasts));
    }

    @Test
    void packingItemsMapsSnowAndFreezingToWarmOuterwear() {
        List<WeatherForecast> forecasts = List.of(forecast(WeatherCondition.SNOW), forecast(WeatherCondition.FREEZING));

        assertEquals(List.of("warm outerwear"), TravelRecommendationRules.packingItems(forecasts));
    }

    @Test
    void packingItemsMapsHotToHydration() {
        List<WeatherForecast> forecasts = List.of(forecast(WeatherCondition.HOT));

        assertEquals(List.of("hydration"), TravelRecommendationRules.packingItems(forecasts));
    }

    @Test
    void packingItemsRemovesDuplicatesAndIsDeterministic() {
        List<WeatherForecast> forecasts = List.of(
            forecast(WeatherCondition.RAIN),
            forecast(WeatherCondition.RAIN),
            forecast(WeatherCondition.HOT)
        );

        List<String> first = TravelRecommendationRules.packingItems(forecasts);
        List<String> second = TravelRecommendationRules.packingItems(forecasts);

        assertEquals(first, second, "packing items must be deterministic");
        assertEquals(first.size(), first.stream().distinct().count(), "packing items must not contain duplicates");
        assertEquals(1, first.stream().filter("umbrella"::equals).count());
    }

    // ---------- sortPlaces ----------

    @Test
    void sortPlacesByDistanceAscendingWithNameTieBreaker() {
        List<PlaceCandidate> input = List.of(
            place("p3", "Zulu", PlaceCategory.MUSEUM, 100, null, null, null, null),
            place("p1", "Alpha", PlaceCategory.MUSEUM, 100, null, null, null, null),
            place("p2", "Mike", PlaceCategory.MUSEUM, 300, null, null, null, null),
            place("p4", "Beta", PlaceCategory.MUSEUM, 200, null, null, null, null)
        );

        List<PlaceCandidate> sorted = TravelRecommendationRules.sortPlaces(input, RecommendationSort.DISTANCE);

        assertEquals(List.of("Alpha", "Zulu", "Beta", "Mike"), names(sorted));
    }

    @Test
    void sortPlacesByPriceAscendingWithNullsLastAndNameTieBreaker() {
        List<PlaceCandidate> input = List.of(
            place("p1", "NoPrice", PlaceCategory.RESTAURANT, 0, null, null, null, null),
            place("p2", "ZedCafe", PlaceCategory.RESTAURANT, 0, 20.0, null, "src", null),
            place("p3", "AceCafe", PlaceCategory.RESTAURANT, 0, 20.0, null, "src", null),
            place("p4", "Mid", PlaceCategory.RESTAURANT, 0, 30.0, null, "src", null)
        );

        List<PlaceCandidate> sorted = TravelRecommendationRules.sortPlaces(input, RecommendationSort.PRICE);

        assertEquals(List.of("AceCafe", "ZedCafe", "Mid", "NoPrice"), names(sorted));
    }

    @Test
    void sortPlacesByRatingDescendingWithNullsLastAndNameTieBreaker() {
        List<PlaceCandidate> input = List.of(
            place("p1", "NoRating", PlaceCategory.RESTAURANT, 0, null, null, null, null),
            place("p2", "ZedCafe", PlaceCategory.RESTAURANT, 0, null, 4.5, null, "src"),
            place("p3", "AceCafe", PlaceCategory.RESTAURANT, 0, null, 4.5, null, "src"),
            place("p4", "Top", PlaceCategory.RESTAURANT, 0, null, 4.8, null, "src")
        );

        List<PlaceCandidate> sorted = TravelRecommendationRules.sortPlaces(input, RecommendationSort.RATING);

        assertEquals(List.of("Top", "AceCafe", "ZedCafe", "NoRating"), names(sorted));
    }

    @Test
    void sortPlacesDoesNotMutateInputAndLimitsToFive() {
        List<PlaceCandidate> input = new ArrayList<>(List.of(
            place("p1", "One", PlaceCategory.RESTAURANT, 500, null, null, null, null),
            place("p2", "Two", PlaceCategory.RESTAURANT, 200, null, null, null, null),
            place("p3", "Three", PlaceCategory.RESTAURANT, 400, null, null, null, null),
            place("p4", "Four", PlaceCategory.RESTAURANT, 100, null, null, null, null),
            place("p5", "Five", PlaceCategory.RESTAURANT, 300, null, null, null, null),
            place("p6", "Six", PlaceCategory.RESTAURANT, 600, null, null, null, null),
            place("p7", "Seven", PlaceCategory.RESTAURANT, 150, null, null, null, null)
        ));
        List<PlaceCandidate> before = List.copyOf(input);

        List<PlaceCandidate> sorted = TravelRecommendationRules.sortPlaces(input, RecommendationSort.DISTANCE);

        assertEquals(before, input, "sortPlaces must not mutate the input list");
        assertEquals(5, sorted.size());
        assertEquals(List.of("Four", "Seven", "Two", "Five", "Three"), names(sorted));
    }

    // ---------- WeatherForecast ----------

    @Test
    void weatherForecastCarriesAllFieldsAndHasNoSetters() {
        WeatherForecast forecast = new WeatherForecast(
            LocalDate.of(2026, 8, 15), WeatherCondition.RAIN, 24.0, "provider", "source", FETCHED_AT
        );

        assertEquals(LocalDate.of(2026, 8, 15), forecast.date());
        assertEquals(WeatherCondition.RAIN, forecast.condition());
        assertEquals(24.0, forecast.temperature());
        assertEquals("provider", forecast.provider());
        assertEquals("source", forecast.source());
        assertEquals(FETCHED_AT, forecast.fetchedAt());
        assertTrue(hasNoSetters(WeatherForecast.class));
    }

    @Test
    void weatherForecastAllowsNullTemperature() {
        WeatherForecast forecast = new WeatherForecast(
            LocalDate.of(2026, 8, 15), WeatherCondition.HOT, null, "provider", "source", FETCHED_AT
        );

        assertNull(forecast.temperature());
    }

    // ---------- PlaceCandidate ----------

    @Test
    void placeCandidateCarriesAllFieldsAndHasNoSetters() {
        PlaceCandidate place = new PlaceCandidate(
            "p1", "Cafe", PlaceCategory.RESTAURANT, 120, 15.0, 4.5,
            "price-src", "rating-src", "provider", "source", FETCHED_AT
        );

        assertEquals("p1", place.id());
        assertEquals("Cafe", place.name());
        assertEquals(PlaceCategory.RESTAURANT, place.category());
        assertEquals(120, place.distanceMeters());
        assertEquals(15.0, place.price());
        assertEquals(4.5, place.rating());
        assertEquals("price-src", place.priceSource());
        assertEquals("rating-src", place.ratingSource());
        assertEquals("provider", place.provider());
        assertEquals("source", place.source());
        assertEquals(FETCHED_AT, place.fetchedAt());
        assertTrue(hasNoSetters(PlaceCandidate.class));
    }

    @Test
    void placeCandidateRequiresPriceSourceWhenPricePresent() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceCandidate(
            "p1", "Cafe", PlaceCategory.RESTAURANT, 120, 15.0, null,
            null, null, "provider", "source", FETCHED_AT
        ));
    }

    @Test
    void placeCandidateRequiresRatingSourceWhenRatingPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceCandidate(
            "p1", "Cafe", PlaceCategory.RESTAURANT, 120, null, 4.5,
            null, null, "provider", "source", FETCHED_AT
        ));
    }

    // ---------- ProviderFailure ----------

    @Test
    void providerFailureCarriesWeatherDateAsCategory() {
        ProviderFailure failure = new ProviderFailure("WEATHER", "2026-08-19", ProviderOutcome.Kind.TIMEOUT);

        assertEquals("WEATHER", failure.stage());
        assertEquals("2026-08-19", failure.category());
        assertEquals(ProviderOutcome.Kind.TIMEOUT, failure.kind());
    }

    @Test
    void providerFailureCarriesPlaceCategoryName() {
        ProviderFailure failure = new ProviderFailure("PLACE", "ACCOMMODATION", ProviderOutcome.Kind.EMPTY);

        assertEquals("PLACE", failure.stage());
        assertEquals("ACCOMMODATION", failure.category());
        assertEquals(ProviderOutcome.Kind.EMPTY, failure.kind());
    }

    @Test
    void providerFailureRejectsBlankOrNullStage() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("", "2026-08-19", ProviderOutcome.Kind.TIMEOUT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure(" ", "2026-08-19", ProviderOutcome.Kind.TIMEOUT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure(null, "2026-08-19", ProviderOutcome.Kind.TIMEOUT));
    }

    @Test
    void providerFailureRejectsBlankOrNullCategory() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("WEATHER", "", ProviderOutcome.Kind.TIMEOUT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("WEATHER", " ", ProviderOutcome.Kind.TIMEOUT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("WEATHER", null, ProviderOutcome.Kind.TIMEOUT));
    }

    @Test
    void providerFailureRejectsNullOrSuccessKind() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("WEATHER", "2026-08-19", null));
        assertThrows(IllegalArgumentException.class,
            () -> new ProviderFailure("WEATHER", "2026-08-19", ProviderOutcome.Kind.SUCCESS));
    }

    // ---------- helpers ----------

    private static WeatherForecast forecast(WeatherCondition condition) {
        return new WeatherForecast(LocalDate.of(2026, 8, 15), condition, null, "provider", "source", FETCHED_AT);
    }

    private static PlaceCandidate place(String id, String name, PlaceCategory category, int distanceMeters,
                                        Double price, Double rating, String priceSource, String ratingSource) {
        return new PlaceCandidate(id, name, category, distanceMeters, price, rating,
            priceSource, ratingSource, "provider", "source", FETCHED_AT);
    }

    private static List<String> names(List<PlaceCandidate> places) {
        return places.stream().map(PlaceCandidate::name).toList();
    }

    private static boolean hasNoSetters(Class<?> type) {
        return Arrays.stream(type.getMethods()).noneMatch(method -> method.getName().startsWith("set"));
    }
}
