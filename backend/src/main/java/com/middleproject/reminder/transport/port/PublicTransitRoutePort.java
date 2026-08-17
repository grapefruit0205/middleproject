package com.middleproject.reminder.transport.port;

import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.PublicTransitRoutePreview;
import com.middleproject.reminder.transport.domain.TransportOutcome;

public interface PublicTransitRoutePort {
    TransportOutcome<PublicTransitRoutePreview> preview(LandmarkCandidate origin, LandmarkCandidate destination);
}
