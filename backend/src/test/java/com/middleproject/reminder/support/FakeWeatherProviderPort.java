package com.middleproject.reminder.support;

import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.WeatherForecast;
import com.middleproject.reminder.port.WeatherProviderPort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic in-memory weather provider for tests. Outcomes are queued per requested
 * date: the first call for a date serves the first queued outcome, and once a date's queue
 * is exhausted the provider succeeds with a default forecast. Call counts and request
 * history are recorded; no static mutable state, network, or sleeping.
 */
public class FakeWeatherProviderPort implements WeatherProviderPort {

    private final Map<LocalDate, List<ProviderOutcome<WeatherForecast>>> queue = new LinkedHashMap<>();
    private final Map<LocalDate, List<WeatherForecast>> history = new LinkedHashMap<>();
    private final Clock clock;

    public FakeWeatherProviderPort(Clock clock) {
        this.clock = clock;
    }

    /** Queues an outcome for the next call for the given date. */
    public FakeWeatherProviderPort queue(LocalDate date, ProviderOutcome<WeatherForecast> outcome) {
        queue.computeIfAbsent(date, d -> new ArrayList<>()).add(outcome);
        return this;
    }

    public void reset() {
        queue.clear();
        history.clear();
    }

    public int callCount() {
        return history.values().stream().mapToInt(List::size).sum();
    }

    public int callCount(LocalDate date) {
        return history.getOrDefault(date, List.of()).size();
    }

    /** Requests recorded per date, in call order. */
    public Map<LocalDate, List<WeatherForecast>> requests() {
        Map<LocalDate, List<WeatherForecast>> copy = new LinkedHashMap<>();
        history.forEach((date, calls) -> copy.put(date, List.copyOf(calls)));
        return copy;
    }

    @Override
    public ProviderOutcome<WeatherForecast> forecast(String destination, LocalDate date) {
        List<ProviderOutcome<WeatherForecast>> outcomes = queue.get(date);
        ProviderOutcome<WeatherForecast> outcome;
        if (outcomes != null && !outcomes.isEmpty()) {
            outcome = outcomes.remove(0);
        } else {
            Instant fetchedAt = clock.instant();
            outcome = new ProviderOutcome.Success<>(new WeatherForecast(date,
                    com.middleproject.reminder.domain.WeatherCondition.RAIN, 18.0, "fake-weather", "fake", fetchedAt));
        }
        history.computeIfAbsent(date, d -> new ArrayList<>()).add(
                outcome.success() ? outcome.value() : new WeatherForecast(date,
                        com.middleproject.reminder.domain.WeatherCondition.RAIN, null, "fake-weather", "fake",
                        clock.instant()));
        return outcome;
    }
}
