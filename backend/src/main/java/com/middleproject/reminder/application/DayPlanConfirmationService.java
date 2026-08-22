package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.DayPlan;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.DayPlanStatus;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleItem;
import com.middleproject.reminder.domain.ScheduleItemStatus;
import com.middleproject.reminder.domain.TravelLeg;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.port.DayPlanRepository;
import com.middleproject.reminder.port.ScheduleItemRepository;
import com.middleproject.reminder.port.TravelLegRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit confirmation boundary for a complete day plan. All domain rows, reminders and
 * scheduler outbox rows are written in one transaction and the request is idempotent.
 */
@Service
public class DayPlanConfirmationService {
    private static final String NOTIFICATION_CHANNEL = "PUSH";
    private static final int DEFAULT_NOTIFICATION_LEAD_MINUTES = 15;

    private final DayPlanPreviewService previews;
    private final DayPlanRoutePreviewService routePreviews;
    private final DayPlanRepository dayPlans;
    private final ScheduleItemRepository items;
    private final TravelLegRepository legs;
    private final IdempotencyService idempotency;
    private final DemoOwnerContext owner;
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DayPlanConfirmationService(DayPlanPreviewService previews, DayPlanRoutePreviewService routePreviews,
                                      DayPlanRepository dayPlans,
                                      ScheduleItemRepository items, TravelLegRepository legs,
                                      IdempotencyService idempotency, DemoOwnerContext owner,
                                      JdbcTemplate db, ObjectMapper mapper, Clock clock) {
        this.previews = previews;
        this.routePreviews = routePreviews;
        this.dayPlans = dayPlans;
        this.items = items;
        this.legs = legs;
        this.idempotency = idempotency;
        this.owner = owner;
        this.db = db;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationResult confirm(DayPlanDraft draft, String confirmationId, String idempotencyKey) {
        if (draft == null || draft.planDate() == null) throw badRequest("planDate is required");
        if (confirmationId == null || confirmationId.isBlank() || confirmationId.length() > 200) {
            throw badRequest("confirmationId must be nonblank and at most 200 characters");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw badRequest("idempotencyKey must be nonblank and at most 200 characters");
        }
        String scope = "day-plan:confirm:" + owner.ownerId() + ":" + draft.planDate();
        return idempotency.execute(scope, idempotencyKey, new Object[]{draft, confirmationId}, ConfirmationResult.class,
                () -> persist(draft, confirmationId));
    }

    private ConfirmationResult persist(DayPlanDraft draft, String confirmationId) {
        DayPlanRoutePreviewService.PreviewResult routePreview = routePreviews.preview(draft);
        DayPlanPreviewService.PlanPreview preview = previews.preview(draft, routePreview);
        if (preview.status() != DayPlanPreviewService.PreviewStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Day plan is not ready for confirmation: " + preview.status());
        }

        String ownerId = owner.ownerId();
        UUID planId = UUID.randomUUID();
        DayPlan draftPlan = new DayPlan(planId, ownerId, draft.planDate(), draft.timezone(), DayPlanStatus.DRAFT, 0);
        dayPlans.insert(draftPlan);
        db.update("update day_plans set draft_json=? where id=? and owner_id=?", draftJson(draft), planId, ownerId);
        if (!dayPlans.transition(planId, ownerId, DayPlanStatus.DRAFT, DayPlanStatus.PROPOSED, 0)
                || !dayPlans.transition(planId, ownerId, DayPlanStatus.PROPOSED, DayPlanStatus.CONFIRMED, 1)) {
            throw conflict("day plan confirmation transition failed");
        }

        Map<Integer, UUID> itemIds = new HashMap<>();
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem source = draft.items().get(i);
            DayPlanRoutePreviewService.ResolvedPlace place = preview.resolvedPlaces().get(i + 1);
            UUID itemId = UUID.randomUUID();
            itemIds.put(i, itemId);
            Integer duration = source.durationMinutes();
            if (duration == null && source.startsAt() != null && source.endsAt() != null) {
                duration = Math.toIntExact(java.time.Duration.between(source.startsAt(), source.endsAt()).toMinutes());
            }
            items.insert(new ScheduleItem(itemId, planId, source.title(), source.timeType(), source.startsAt(),
                    source.endsAt(), duration == null ? 0 : duration, place.name(), place.address(), place.coordinates(),
                    i, ScheduleItemStatus.PLANNED, 0), ownerId);
        }

        Instant fetchedAt = routePreview.fetchedAt();
        List<TravelLeg> persistedLegs = new ArrayList<>();
        for (DayPlanRoutePreviewService.TravelLegPreview leg : routePreview.legs()) {
            UUID toItemId = itemIds.get(leg.toItemIndex());
            UUID fromItemId = leg.fromItemIndex() < 0 ? null : itemIds.get(leg.fromItemIndex());
            persistedLegs.add(new TravelLeg(UUID.randomUUID(), planId, fromItemId, toItemId, leg.mode(),
                    leg.durationMinutes(), leg.bufferMinutes(), leg.departureAt(), leg.arrivalAt(),
                    leg.provider(), leg.source(), fetchedAt, leg.toItemIndex(), 0));
        }
        for (TravelLeg leg : persistedLegs) legs.insert(leg, ownerId);

        List<UUID> reminderIds = new ArrayList<>();
        int lead = draft.notificationLeadMinutes() == null ? DEFAULT_NOTIFICATION_LEAD_MINUTES : draft.notificationLeadMinutes();
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem source = draft.items().get(i);
            if (source.startsAt() == null) {
                continue;
            }
            UUID eventId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID reminderId = UUID.randomUUID();
            OffsetDateTime notificationAt = source.startsAt().minusMinutes(lead);
            db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                    eventId, source.title(), source.startsAt(), source.endsAt(), now, now);
            db.update("insert into notification_policies(id,channel,lead_minutes,created_at,updated_at,version) values(?,?,?,?,?,0)",
                    policyId, NOTIFICATION_CHANNEL, lead, now, now);
            db.update("insert into reminders(id,event_id,policy_id,owner_id,schedule_item_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)",
                    reminderId, eventId, policyId, ownerId, itemIds.get(i), "SCHEDULE_PENDING", now, now);
            db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload) values(?,?,?,?,?,?,?)",
                    UUID.randomUUID(), reminderId, "UPSERT", 0, 1, notificationAt, payload(reminderId, 1));
            reminderIds.add(reminderId);
        }
        DayPlan confirmed = dayPlans.findByIdForOwner(planId, ownerId).orElseThrow();
        return new ConfirmationResult(confirmed, preview, List.copyOf(reminderIds), confirmationId);
    }

    private String payload(UUID reminderId, long schedulerVersion) {
        try {
            return mapper.writeValueAsString(Map.of("reminderId", reminderId.toString(),
                    "schedulerVersion", schedulerVersion, "idempotencyKey", reminderId + ":" + schedulerVersion));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String draftJson(DayPlanDraft draft) {
        try {
            return mapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize day plan draft", e);
        }
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record ConfirmationResult(DayPlan plan, DayPlanPreviewService.PlanPreview preview,
                                     List<UUID> reminderIds, String confirmationId) {
        public ConfirmationResult {
            reminderIds = List.copyOf(reminderIds);
        }
    }
}
