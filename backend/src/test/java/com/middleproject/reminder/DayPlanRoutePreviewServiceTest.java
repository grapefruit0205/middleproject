package com.middleproject.reminder;

import com.middleproject.reminder.application.DayPlanRoutePreviewService;
import com.middleproject.reminder.application.DayPlanValidationService;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.DayPlanRouteEstimate;
import com.middleproject.reminder.domain.DayPlanRouteRequest;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import com.middleproject.reminder.port.DayPlanRouteProvider;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.application.PlaceDiscoveryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayPlanRoutePreviewServiceTest {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 1);
    private static final OffsetDateTime FIRST_START = OffsetDateTime.parse("2030-01-01T09:00:00+09:00");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesConfirmedCoordinatesAndBuildsReadOnlyLegsWithBuffer() {
        var provider = new FakeRouteProvider(30);
        var service = service(provider, coordinatesOnly());
        var first = item("병원", "병원", FIRST_START, "SUBWAY");
        var second = item("약속", "식당", OffsetDateTime.parse("2030-01-01T11:00:00+09:00"), "BUS");

        var result = service.preview(new DayPlanDraft(DATE, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0),
                List.of(first, second), 15, false));

        assertEquals(DayPlanRoutePreviewService.PreviewStatus.READY, result.status());
        assertTrue(result.issues().isEmpty());
        assertEquals(2, result.legs().size());
        assertEquals(-1, result.legs().get(0).fromItemIndex());
        assertEquals(0, result.legs().get(0).toItemIndex());
        assertEquals(OffsetDateTime.parse("2030-01-01T08:20:00+09:00"), result.legs().get(0).departureAt());
        assertEquals(OffsetDateTime.parse("2030-01-01T08:50:00+09:00"), result.legs().get(0).arrivalAt());
        assertEquals("fake-route", result.legs().get(0).provider());
        assertEquals(2, provider.requests.size());
    }

    @Test
    void stopsForAmbiguousPlaceBeforeCallingRouteProvider() {
        var provider = new FakeRouteProvider(30);
        var places = new PlaceDiscoveryService(null) {
            @Override
            public TransportOutcome<List<LandmarkCandidate>> resolve(String query, int limit) {
                return TransportOutcome.success(List.of(
                        new LandmarkCandidate("강남역", "서울", 37.4979, 127.0276),
                        new LandmarkCandidate("강남역", "부산", 35.1, 129.0)));
            }
        };
        var service = service(provider, places);
        var item = item("병원", "강남역", FIRST_START, "SUBWAY");

        var result = service.preview(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, null,
                List.of(item), 15, false));

        assertEquals(DayPlanRoutePreviewService.PreviewStatus.PLACE_SELECTION_REQUIRED, result.status());
        assertEquals(1, result.placeSelections().size());
        assertEquals("originName", result.placeSelections().get(0).path());
        assertTrue(result.legs().isEmpty());
        assertTrue(provider.requests.isEmpty());
    }

    @Test
    void reportsRouteFailureWithoutPersistingOrInventingTimes() {
        var places = coordinatesOnly();
        var service = service(new DayPlanRouteProvider() {
            @Override
            public ProviderOutcome<DayPlanRouteEstimate> estimate(DayPlanRouteRequest request) {
                return new ProviderOutcome.Empty<>();
            }
        }, places);
        var item = item("병원", "병원", FIRST_START, "SUBWAY");

        var result = service.preview(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, new GeoPoint(37.5, 127.0),
                List.of(item), 15, false));

        assertEquals(DayPlanRoutePreviewService.PreviewStatus.ROUTE_UNAVAILABLE, result.status());
        assertFalse(result.issues().isEmpty());
        assertEquals("ROUTE_UNAVAILABLE", result.issues().getFirst().code());
        assertTrue(result.legs().isEmpty());
    }

    @Test
    void rejectsLegThatCannotFitBetweenFixedItems() {
        var service = service(new FakeRouteProvider(80), coordinatesOnly());
        var first = item("병원", "병원", FIRST_START, "SUBWAY");
        var second = item("약속", "식당", OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), "SUBWAY");

        var result = service.preview(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, new GeoPoint(37.5, 127.0),
                List.of(first, second), 15, false));

        assertEquals(DayPlanRoutePreviewService.PreviewStatus.INVALID_TIMELINE, result.status());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("TRAVEL_WINDOW_CONFLICT")));
    }

    @Test
    void allowsFlexibleItemWithoutInventingATimedLeg() {
        var provider = new FakeRouteProvider(30);
        var flexible = new ScheduleDraftItem("서점", ScheduleTimeType.FLEXIBLE, null, null,
                30, "교보문고", "서울", new GeoPoint(37.51, 127.01), "SUBWAY");

        var result = service(provider, coordinatesOnly()).preview(new DayPlanDraft(
                DATE, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0),
                List.of(flexible), 15, false));

        assertEquals(DayPlanRoutePreviewService.PreviewStatus.READY, result.status());
        assertTrue(result.legs().isEmpty());
        assertTrue(result.issues().isEmpty());
        assertTrue(provider.requests.isEmpty());
    }

    private static ScheduleDraftItem item(String title, String place, OffsetDateTime start, String mode) {
        return new ScheduleDraftItem(title, ScheduleTimeType.FIXED_START, start, start.plusMinutes(60),
                60, place, "서울", new GeoPoint(37.51, 127.01), mode);
    }

    private static PlaceDiscoveryService coordinatesOnly() {
        return new PlaceDiscoveryService(null) {
            @Override
            public TransportOutcome<List<LandmarkCandidate>> resolve(String query, int limit) {
                return TransportOutcome.empty();
            }
        };
    }

    private static DayPlanRoutePreviewService service(DayPlanRouteProvider provider, PlaceDiscoveryService places) {
        return new DayPlanRoutePreviewService(new DayPlanValidationService(), places, provider, CLOCK);
    }

    private static final class FakeRouteProvider implements DayPlanRouteProvider {
        private final int durationMinutes;
        private final List<DayPlanRouteRequest> requests = new java.util.ArrayList<>();

        private FakeRouteProvider(int durationMinutes) { this.durationMinutes = durationMinutes; }

        @Override
        public ProviderOutcome<DayPlanRouteEstimate> estimate(DayPlanRouteRequest request) {
            requests.add(request);
            return new ProviderOutcome.Success<>(new DayPlanRouteEstimate(durationMinutes, "fake-route", "test", null));
        }
    }
}
