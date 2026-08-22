package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Combines event anchors and read-only travel legs into the timeline shown before confirmation.
 * This boundary deliberately has no repository, notification, or scheduler dependency.
 */
@Service
public class DayPlanPreviewService {
    public static final int DEFAULT_NOTIFICATION_LEAD_MINUTES = 15;

    private final DayPlanRoutePreviewService routes;
    private final Clock clock;

    public DayPlanPreviewService(DayPlanRoutePreviewService routes, Clock clock) {
        this.routes = routes;
        this.clock = clock;
    }

    public PlanPreview preview(DayPlanDraft draft) {
        return preview(draft, routes.preview(draft));
    }

    /**
     * Renders a previously computed route preview without calling a provider again.  The
     * confirmation boundary uses this overload so persisted legs and the user-facing timeline
     * are derived from the exact same provider response.
     */
    public PlanPreview preview(DayPlanDraft draft, DayPlanRoutePreviewService.PreviewResult routePreview) {
        if (routePreview == null) {
            throw new IllegalArgumentException("routePreview is required");
        }
        if (routePreview.status() != DayPlanRoutePreviewService.PreviewStatus.READY) {
            return new PlanPreview(mapStatus(routePreview.status()), draft == null ? null : draft.planDate(),
                    draft == null ? null : draft.timezone(), draft == null ? null : draft.originName(),
                    routePreview.resolvedPlaces(), routePreview.placeSelections(), List.of(), routePreview.issues(),
                    draft != null && draft.wakeAlarmRequested(), routePreview.fetchedAt());
        }

        int lead = draft.notificationLeadMinutes() == null
                ? DEFAULT_NOTIFICATION_LEAD_MINUTES : draft.notificationLeadMinutes();
        List<TimelineEntry> entries = new ArrayList<>();
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem item = draft.items().get(i);
            OffsetDateTime start = item.startsAt();
            OffsetDateTime end = item.endsAt();
            if (end == null && start != null && item.durationMinutes() != null) {
                end = start.plusMinutes(item.durationMinutes());
            }
            if (start != null && end != null) {
                entries.add(new TimelineEntry(EntryKind.EVENT, item.title(), item.placeName(),
                        start, end, null, null, null, null, start.minusMinutes(lead), i));
            }
        }
        for (DayPlanRoutePreviewService.TravelLegPreview leg : routePreview.legs()) {
            String destination = routePreview.resolvedPlaces().get(leg.toItemIndex() + 1).name();
            entries.add(new TimelineEntry(EntryKind.TRAVEL, "이동: " + destination, destination,
                    leg.departureAt(), leg.arrivalAt(), leg.mode(), leg.provider(), leg.source(),
                    leg.handoffUrl(), null, leg.toItemIndex()));
        }
        entries.sort(Comparator.comparing(TimelineEntry::startsAt)
                .thenComparing(entry -> entry.kind() == EntryKind.TRAVEL ? 0 : 1)
                .thenComparingInt(TimelineEntry::sequence));
        return new PlanPreview(PreviewStatus.READY, draft.planDate(), draft.timezone(), draft.originName(),
                routePreview.resolvedPlaces(), List.of(), List.copyOf(entries), List.of(),
                draft.wakeAlarmRequested(), routePreview.fetchedAt() == null ? Instant.now(clock) : routePreview.fetchedAt());
    }

    private PreviewStatus mapStatus(DayPlanRoutePreviewService.PreviewStatus status) {
        return switch (status) {
            case READY -> PreviewStatus.READY;
            case INVALID_DRAFT -> PreviewStatus.INVALID_DRAFT;
            case PLACE_SELECTION_REQUIRED -> PreviewStatus.PLACE_SELECTION_REQUIRED;
            case PLACE_RESOLUTION_FAILED -> PreviewStatus.PLACE_RESOLUTION_FAILED;
            case ROUTE_UNAVAILABLE -> PreviewStatus.ROUTE_UNAVAILABLE;
            case INVALID_TIMELINE -> PreviewStatus.INVALID_TIMELINE;
        };
    }

    public enum PreviewStatus {
        READY, INVALID_DRAFT, PLACE_SELECTION_REQUIRED, PLACE_RESOLUTION_FAILED, ROUTE_UNAVAILABLE, INVALID_TIMELINE
    }

    public enum EntryKind { EVENT, TRAVEL }

    public record TimelineEntry(EntryKind kind, String title, String placeName,
                                OffsetDateTime startsAt, OffsetDateTime endsAt,
                                String mode, String provider, String source, String handoffUrl,
                                OffsetDateTime notificationAt, int sequence) {}

    public record PlanPreview(PreviewStatus status, java.time.LocalDate planDate, String timezone,
                               String originName, List<DayPlanRoutePreviewService.ResolvedPlace> resolvedPlaces,
                               List<DayPlanRoutePreviewService.PlaceSelection> placeSelections,
                               List<TimelineEntry> entries, List<DayPlanRoutePreviewService.PreviewIssue> issues,
                               boolean wakeAlarmRequested, Instant generatedAt) {
        public PlanPreview {
            resolvedPlaces = List.copyOf(resolvedPlaces);
            placeSelections = List.copyOf(placeSelections);
            entries = List.copyOf(entries);
            issues = List.copyOf(issues);
        }
    }
}
