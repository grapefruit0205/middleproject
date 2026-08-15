package com.middleproject.reminder.support;

import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceCategory;
import com.middleproject.reminder.domain.PlaceSearchRequest;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.port.PlaceSearchProviderPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic in-memory place-search provider for tests. Outcomes are queued per category:
 * the first call for a category serves the first queued outcome, and once a category's queue
 * is exhausted the provider succeeds with default candidates. Call counts and request
 * history are recorded; no static mutable state, network, or sleeping.
 */
public class FakePlaceSearchProviderPort implements PlaceSearchProviderPort {

    private final Map<PlaceCategory, List<ProviderOutcome<List<PlaceCandidate>>>> queue = new LinkedHashMap<>();
    private final Map<PlaceCategory, List<PlaceSearchRequest>> history = new LinkedHashMap<>();
    private final Clock clock;

    public FakePlaceSearchProviderPort(Clock clock) {
        this.clock = clock;
    }

    /** Queues an outcome for the next call for the given category. */
    public FakePlaceSearchProviderPort queue(PlaceCategory category, ProviderOutcome<List<PlaceCandidate>> outcome) {
        queue.computeIfAbsent(category, c -> new ArrayList<>()).add(outcome);
        return this;
    }

    public void reset() {
        queue.clear();
        history.clear();
    }

    public int callCount() {
        return history.values().stream().mapToInt(List::size).sum();
    }

    public int callCount(PlaceCategory category) {
        return history.getOrDefault(category, List.of()).size();
    }

    /** Requests recorded per category, in call order. */
    public Map<PlaceCategory, List<PlaceSearchRequest>> requests() {
        Map<PlaceCategory, List<PlaceSearchRequest>> copy = new LinkedHashMap<>();
        history.forEach((category, calls) -> copy.put(category, List.copyOf(calls)));
        return copy;
    }

    @Override
    public ProviderOutcome<List<PlaceCandidate>> search(PlaceSearchRequest request) {
        List<ProviderOutcome<List<PlaceCandidate>>> outcomes = queue.get(request.category());
        ProviderOutcome<List<PlaceCandidate>> outcome;
        if (outcomes != null && !outcomes.isEmpty()) {
            outcome = outcomes.remove(0);
        } else {
            Instant fetchedAt = clock.instant();
            List<PlaceCandidate> defaults = switch (request.category()) {
                case RESTAURANT -> List.of(
                        candidate("fake-rest-1", "Fake Cafe A", request.category(), 500, 12_000.0, 4.3, fetchedAt),
                        candidate("fake-rest-2", "Fake Cafe B", request.category(), 300, 20_000.0, 4.6, fetchedAt));
                case ACCOMMODATION -> List.of(
                        candidate("fake-stay-1", "Fake Hotel A", request.category(), 4_000, 110_000.0, 4.4, fetchedAt),
                        candidate("fake-stay-2", "Fake Hotel B", request.category(), 2_000, 60_000.0, 4.2, fetchedAt));
                case ATTRACTION -> List.of(
                        candidate("fake-attr-1", "Fake Tower", request.category(), 1_000, null, 4.5, fetchedAt),
                        candidate("fake-attr-2", "Fake Garden", request.category(), 3_000, null, 4.1, fetchedAt));
                case MUSEUM -> List.of();
            };
            outcome = new ProviderOutcome.Success<>(defaults);
        }
        history.computeIfAbsent(request.category(), c -> new ArrayList<>()).add(request);
        return outcome;
    }

    private static PlaceCandidate candidate(String id, String name, PlaceCategory category, int distanceMeters,
                                            Double price, Double rating, Instant fetchedAt) {
        return new PlaceCandidate(id, name, category, distanceMeters, price, rating,
                price == null ? null : "fake-price",
                rating == null ? null : "fake-rating",
                "fake-place", "fake", fetchedAt);
    }
}
