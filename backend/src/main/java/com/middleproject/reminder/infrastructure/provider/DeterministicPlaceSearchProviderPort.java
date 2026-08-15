package com.middleproject.reminder.infrastructure.provider;

import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceCategory;
import com.middleproject.reminder.domain.PlaceSearchRequest;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.port.PlaceSearchProviderPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic in-memory place-search adapter so the normal Spring Boot context starts
 * without network access or credentials. Candidates are immutable, carry
 * provider/source/fetchedAt provenance, and prices/ratings carry their own source.
 * Tests override this bean with a {@code @Primary} fake.
 */
@Component
public class DeterministicPlaceSearchProviderPort implements PlaceSearchProviderPort {

    private final Clock clock;

    public DeterministicPlaceSearchProviderPort(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProviderOutcome<List<PlaceCandidate>> search(PlaceSearchRequest request) {
        Instant fetchedAt = clock.instant();
        return switch (request.category()) {
            case RESTAURANT -> new ProviderOutcome.Success<>(List.of(
                    candidate("demo-rest-1", "Demo Hanjeongsik", request.category(), 400, 18_000.0, 4.6,
                            "demo", fetchedAt),
                    candidate("demo-rest-2", "Demo Noodle House", request.category(), 800, 9_000.0, 4.2,
                            "demo", fetchedAt)));
            case ACCOMMODATION -> new ProviderOutcome.Success<>(List.of(
                    candidate("demo-stay-1", "Demo Seaside Hotel", request.category(), 2_500, 120_000.0, 4.5,
                            "demo", fetchedAt),
                    candidate("demo-stay-2", "Demo Guesthouse", request.category(), 3_100, 45_000.0, 4.1,
                            "demo", fetchedAt)));
            case ATTRACTION -> new ProviderOutcome.Success<>(List.of(
                    candidate("demo-attr-1", "Demo Harbor Park", request.category(), 1_200, null, 4.3,
                            "demo", fetchedAt),
                    candidate("demo-attr-2", "Demo Old Town", request.category(), 2_000, 5_000.0, 4.7,
                            "demo", fetchedAt)));
            case MUSEUM -> new ProviderOutcome.Success<>(new ArrayList<>());
        };
    }

    private static PlaceCandidate candidate(String id, String name, PlaceCategory category, int distanceMeters,
                                            Double price, Double rating, String source, Instant fetchedAt) {
        return new PlaceCandidate(id, name, category, distanceMeters, price, rating,
                price == null ? null : source + "-price",
                rating == null ? null : source + "-rating",
                "demo-place", source, fetchedAt);
    }
}
