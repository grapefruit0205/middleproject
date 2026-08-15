package com.middleproject.reminder.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable weather forecast for a single date. Temperature may be unknown (null).
 */
public record WeatherForecast(LocalDate date, WeatherCondition condition, Double temperature,
                              String provider, String source, Instant fetchedAt) {

    public WeatherForecast {
        if (date == null) throw new IllegalArgumentException("date is required");
        if (condition == null) throw new IllegalArgumentException("condition is required");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        if (fetchedAt == null) throw new IllegalArgumentException("fetchedAt is required");
    }
}
