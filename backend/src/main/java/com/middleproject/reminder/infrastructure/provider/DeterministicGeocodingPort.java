package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.port.GeocodingPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Deterministic in-memory geocoding adapter so the normal Spring Boot context starts without
 * network access or credentials. Tests override this bean with a {@code @Primary} fake.
 */
@Component
public class DeterministicGeocodingPort implements GeocodingPort {

    private static final Map<String, GeoPoint> POINTS = Map.of(
            "Seoul", new GeoPoint(37.5665, 126.9780),
            "Busan", new GeoPoint(35.1796, 129.0756),
            "Daejeon", new GeoPoint(36.3504, 127.3845),
            "Gwangju", new GeoPoint(35.1595, 126.8526),
            "Incheon", new GeoPoint(37.4563, 126.7052));

    @Override
    public ProviderOutcome<GeoPoint> geocode(String location) {
        GeoPoint point = POINTS.get(location);
        return point == null ? new ProviderOutcome.Empty<>() : new ProviderOutcome.Success<>(point);
    }
}
