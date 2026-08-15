package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.WeatherCondition;
import com.middleproject.reminder.domain.WeatherForecast;
import com.middleproject.reminder.port.WeatherProviderPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Deterministic in-memory weather adapter so the normal Spring Boot context starts without
 * network access or credentials. Forecasts are derived from the requested date and carry
 * provider/source/fetchedAt provenance. Tests override this bean with a {@code @Primary} fake.
 */
@Component
public class DeterministicWeatherProviderPort implements WeatherProviderPort {

    private final Clock clock;

    public DeterministicWeatherProviderPort(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProviderOutcome<WeatherForecast> forecast(String destination, LocalDate date) {
        WeatherCondition condition = summer(date) ? WeatherCondition.HOT : WeatherCondition.RAIN;
        double temperature = summer(date) ? 31.0 : 17.0;
        Instant fetchedAt = clock.instant();
        return new ProviderOutcome.Success<>(new WeatherForecast(date, condition, temperature,
                "demo-weather", "demo", fetchedAt));
    }

    private static boolean summer(LocalDate date) {
        MonthDay july = MonthDay.of(7, 1);
        MonthDay august = MonthDay.of(8, 31);
        return !MonthDay.from(date).isBefore(july) && !MonthDay.from(date).isAfter(august);
    }
}
