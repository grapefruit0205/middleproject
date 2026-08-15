package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.WeatherForecast;

import java.time.LocalDate;

public interface WeatherProviderPort {

    ProviderOutcome<WeatherForecast> forecast(String destination, LocalDate date);
}
