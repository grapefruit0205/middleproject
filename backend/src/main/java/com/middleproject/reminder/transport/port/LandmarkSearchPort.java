package com.middleproject.reminder.transport.port;

import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.NearbySubwayStation;
import com.middleproject.reminder.transport.domain.TransportOutcome;

import java.util.List;

public interface LandmarkSearchPort {
    TransportOutcome<List<LandmarkCandidate>> search(String query, int limit);

    TransportOutcome<List<NearbySubwayStation>> findNearbySubwayStations(
            double latitude, double longitude, int radiusMeters, int limit);
}
