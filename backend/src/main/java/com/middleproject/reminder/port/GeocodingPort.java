package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;

/**
 * Typed port for geocoding a place name into coordinates. Adapters must return a
 * {@link ProviderOutcome} instead of throwing for provider-side failures.
 */
public interface GeocodingPort {
    ProviderOutcome<GeoPoint> geocode(String location);
}
