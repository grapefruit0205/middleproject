package com.middleproject.reminder.application;

import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.PublicTransitRoutePreview;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.LandmarkSearchPort;
import com.middleproject.reminder.transport.port.PublicTransitRoutePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Resolves named trip endpoints immediately before a read-only transit route preview. */
@Service
public class PublicTransitRoutePreviewService {
    private final LandmarkSearchPort places;
    private final PublicTransitRoutePort routes;

    @Autowired
    public PublicTransitRoutePreviewService(@Autowired(required = false) LandmarkSearchPort places,
                                             @Autowired(required = false) PublicTransitRoutePort routes) {
        this.places = places;
        this.routes = routes;
    }

    public TransportOutcome<PublicTransitRoutePreview> preview(String originQuery, String destinationQuery) {
        if (originQuery == null || originQuery.isBlank() || destinationQuery == null || destinationQuery.isBlank()) {
            return TransportOutcome.malformed("origin and destination must be nonblank");
        }
        if (originQuery.length() > 200 || destinationQuery.length() > 200) {
            return TransportOutcome.malformed("origin and destination must be at most 200 characters");
        }
        if (places == null || routes == null) return TransportOutcome.disabledInsecure("Public transit route preview is disabled");

        TransportOutcome<List<LandmarkCandidate>> origin = places.search(originQuery.trim(), 1);
        if (!origin.isSuccess()) return copyFailure(origin);
        TransportOutcome<List<LandmarkCandidate>> destination = places.search(destinationQuery.trim(), 1);
        if (!destination.isSuccess()) return copyFailure(destination);
        if (origin.value().isEmpty() || destination.value().isEmpty()) return TransportOutcome.empty();
        return routes.preview(origin.value().getFirst(), destination.value().getFirst());
    }

    private static <T> TransportOutcome<T> copyFailure(TransportOutcome<?> outcome) {
        if (outcome.isEmpty()) return TransportOutcome.empty();
        return switch (outcome.failureKind()) {
            case AUTH_REJECTED -> TransportOutcome.authRejected(outcome.errorMessage());
            case RATE_LIMITED -> TransportOutcome.rateLimited(outcome.errorMessage());
            case TIMEOUT -> TransportOutcome.timeout(outcome.errorMessage());
            case DISABLED_INSECURE -> TransportOutcome.disabledInsecure(outcome.errorMessage());
            case MALFORMED -> TransportOutcome.malformed(outcome.errorMessage());
        };
    }
}
