package com.middleproject.reminder.application;

import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.NearbySubwayStation;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.LandmarkSearchPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read-only place and nearby-station discovery. It never reads device background location. */
@Service
public class PlaceDiscoveryService {
    private final LandmarkSearchPort places;

    @Autowired
    public PlaceDiscoveryService(@Autowired(required = false) LandmarkSearchPort places) {
        this.places = places;
    }

    public TransportOutcome<List<LandmarkCandidate>> resolve(String query, int limit) {
        if (query == null || query.isBlank() || query.length() > 200) {
            return TransportOutcome.malformed("place query must be nonblank and at most 200 characters");
        }
        if (limit < 1 || limit > 15) return TransportOutcome.malformed("limit must be between 1 and 15");
        if (places == null) return TransportOutcome.disabledInsecure("Place discovery is disabled");
        return places.search(query.trim(), limit);
    }

    public TransportOutcome<List<NearbySubwayStation>> nearbySubway(
            double latitude, double longitude, int radiusMeters) {
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            return TransportOutcome.malformed("coordinates must be within South Korea");
        }
        if (radiusMeters < 1 || radiusMeters > 20_000) {
            return TransportOutcome.malformed("radiusMeters must be between 1 and 20000");
        }
        if (places == null) return TransportOutcome.disabledInsecure("Place discovery is disabled");
        return places.findNearbySubwayStations(latitude, longitude, radiusMeters, 5);
    }
}
