package com.middleproject.reminder;

import com.middleproject.reminder.application.DayPlanPreviewService;
import com.middleproject.reminder.application.DayPlanRoutePreviewService;
import com.middleproject.reminder.application.DayPlanValidationService;
import com.middleproject.reminder.application.PlaceDiscoveryService;
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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayPlanPreviewServiceTest {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsAnOrderedReadOnlyTimelineAndNotificationPreview() {
        var routePreview = new DayPlanRoutePreviewService(new DayPlanValidationService(), coordinatesOnly(),
                request -> new ProviderOutcome.Success<>(new DayPlanRouteEstimate(30, "fake", "test", null)), CLOCK);
        var service = new DayPlanPreviewService(routePreview, CLOCK);
        var first = item("병원", "병원", "2030-01-01T09:00:00+09:00", "SUBWAY");
        var second = item("뮤지컬", "공연장", "2030-01-01T16:00:00+09:00", "CAR");

        var result = service.preview(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, new GeoPoint(37.5, 127.0),
                List.of(first, second), 15, true));

        assertEquals(DayPlanPreviewService.PreviewStatus.READY, result.status());
        assertEquals(4, result.entries().size());
        assertEquals(DayPlanPreviewService.EntryKind.TRAVEL, result.entries().get(0).kind());
        assertEquals(DayPlanPreviewService.EntryKind.EVENT, result.entries().get(1).kind());
        assertEquals("2030-01-01T08:45+09:00", result.entries().get(1).notificationAt().toString());
        assertEquals("병원", result.entries().get(1).title());
        assertTrue(result.wakeAlarmRequested());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void propagatesPlaceSelectionAndDoesNotCreateAFalseTimeline() {
        var ambiguousPlaces = new PlaceDiscoveryService(null) {
            @Override
            public TransportOutcome<List<LandmarkCandidate>> resolve(String query, int limit) {
                return TransportOutcome.success(List.of(
                        new LandmarkCandidate("집", "서울", 37.5, 127.0),
                        new LandmarkCandidate("집", "부산", 35.1, 129.0)));
            }
        };
        var routePreview = new DayPlanRoutePreviewService(new DayPlanValidationService(), ambiguousPlaces,
                request -> new ProviderOutcome.Success<>(new DayPlanRouteEstimate(30, "fake", "test", null)), CLOCK);
        var result = new DayPlanPreviewService(routePreview, CLOCK).preview(new DayPlanDraft(DATE, "Asia/Seoul",
                "집", null, null, List.of(item("병원", "병원", "2030-01-01T09:00:00+09:00", "SUBWAY")), 15, false));

        assertEquals(DayPlanPreviewService.PreviewStatus.PLACE_SELECTION_REQUIRED, result.status());
        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.placeSelections().size());
    }

    private static ScheduleDraftItem item(String title, String place, String start, String mode) {
        var startsAt = OffsetDateTime.parse(start);
        return new ScheduleDraftItem(title, ScheduleTimeType.FIXED_START, startsAt, startsAt.plusMinutes(60),
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
}
