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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owner-scoped revision boundary for the first destructive conversational edit: removing an
 * itinerary item. The item and its reminder are cancelled, route legs are recomputed from the
 * stored draft, and the operation is idempotent and optimistic-version protected.
 */
@Service
public class DayPlanRevisionService {
    private static final int DEFAULT_NOTIFICATION_LEAD_MINUTES = 15;
    private static final String[] CANCELLABLE_REMINDER_STATUSES = {
            "CREATED", "SCHEDULE_PENDING", "SCHEDULED", "SCHEDULE_FAILED", "DELIVERY_FAILED", "RETRYING"
    };

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

    public DayPlanRevisionService(DayPlanPreviewService previews, DayPlanRoutePreviewService routePreviews,
                                  DayPlanRepository dayPlans, ScheduleItemRepository items,
                                  TravelLegRepository legs, IdempotencyService idempotency,
                                  DemoOwnerContext owner, JdbcTemplate db, ObjectMapper mapper, Clock clock) {
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
    public RevisionResult cancelItem(UUID planId, int sequence, long expectedPlanVersion, String idempotencyKey) {
        if (planId == null) throw badRequest("planId is required");
        if (sequence < 0) throw badRequest("sequence must be nonnegative");
        if (expectedPlanVersion < 0) throw badRequest("expectedPlanVersion must be nonnegative");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw badRequest("idempotencyKey must be nonblank and at most 200 characters");
        }
        String scope = "day-plan:cancel-item:" + owner.ownerId() + ":" + planId;
        return idempotency.execute(scope, idempotencyKey,
                new Object[]{planId, sequence, expectedPlanVersion}, RevisionResult.class,
                () -> revise(planId, sequence, expectedPlanVersion));
    }

    private RevisionResult revise(UUID planId, int sequence, long expectedPlanVersion) {
        String ownerId = owner.ownerId();
        DayPlan plan = dayPlans.findByIdForOwner(planId, ownerId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Day plan not found"));
        if (plan.version() != expectedPlanVersion) throw conflict("day plan version is stale");
        if (plan.status() != DayPlanStatus.CONFIRMED && plan.status() != DayPlanStatus.ACTIVE) {
            throw conflict("only a confirmed or active day plan can be revised");
        }

        List<ScheduleItem> currentItems = items.findAllByPlanForOwner(planId, ownerId);
        if (sequence >= currentItems.size()) throw badRequest("schedule item sequence is out of range");
        ScheduleItem removed = currentItems.get(sequence);
        if (removed.status() == ScheduleItemStatus.COMPLETED || removed.status() == ScheduleItemStatus.CANCELLED) {
            throw conflict("schedule item is already completed or cancelled");
        }

        DayPlanDraft storedDraft = readDraft(planId, ownerId);
        if (sequence >= storedDraft.items().size()) throw conflict("stored day plan draft is inconsistent");
        List<ScheduleDraftItem> remainingDraftItems = new ArrayList<>(storedDraft.items());
        remainingDraftItems.remove(sequence);
        if (remainingDraftItems.isEmpty()) throw badRequest("cancel the whole day plan instead of removing its only item");
        DayPlanDraft revisedDraft = new DayPlanDraft(storedDraft.planDate(), storedDraft.timezone(),
                storedDraft.originName(), storedDraft.originAddress(), storedDraft.originCoordinates(),
                remainingDraftItems, storedDraft.notificationLeadMinutes(), storedDraft.wakeAlarmRequested());

        DayPlanRoutePreviewService.PreviewResult routePreview = routePreviews.preview(revisedDraft);
        DayPlanPreviewService.PlanPreview preview = previews.preview(revisedDraft, routePreview);
        if (preview.status() != DayPlanPreviewService.PreviewStatus.READY) {
            throw conflict("recomputed day plan is not ready: " + preview.status());
        }

        List<CanceledReminder> canceled = cancelReminderForItem(removed.id(), ownerId);
        if (!items.transition(removed.id(), ownerId, removed.status(), ScheduleItemStatus.CANCELLED, removed.version())) {
            throw conflict("schedule item version changed while cancelling");
        }
        db.update("delete from travel_legs where day_plan_id=?", planId);

        Map<Integer, UUID> itemIds = new HashMap<>();
        int revisedItemIndex = 0;
        for (ScheduleItem current : currentItems) {
            if (current.id().equals(removed.id())) continue;
            itemIds.put(revisedItemIndex++, current.id());
        }

        String draftJson = draftJson(revisedDraft);
        int planUpdated = db.update("update day_plans set draft_json=?,updated_at=?,version=version+1 where id=? and owner_id=? and version=?",
                draftJson, OffsetDateTime.now(clock), planId, ownerId, expectedPlanVersion);
        if (planUpdated != 1) throw conflict("day plan version changed while revising");

        for (DayPlanRoutePreviewService.TravelLegPreview leg : routePreview.legs()) {
            UUID toItemId = itemIds.get(leg.toItemIndex());
            UUID fromItemId = leg.fromItemIndex() < 0 ? null : itemIds.get(leg.fromItemIndex());
            if (toItemId == null || (leg.fromItemIndex() >= 0 && fromItemId == null)) {
                throw new IllegalStateException("recomputed route does not match persisted item order");
            }
            legs.insert(new TravelLeg(UUID.randomUUID(), planId, fromItemId, toItemId, leg.mode(),
                    leg.durationMinutes(), leg.bufferMinutes(), leg.departureAt(), leg.arrivalAt(),
                    leg.provider(), leg.source(), routePreview.fetchedAt(), leg.toItemIndex(), 0), ownerId);
        }

        DayPlan updated = dayPlans.findByIdForOwner(planId, ownerId).orElseThrow();
        List<UUID> canceledIds = canceled.stream().map(CanceledReminder::id).toList();
        return new RevisionResult(updated, preview, canceledIds, removed.sequence());
    }

    private List<CanceledReminder> cancelReminderForItem(UUID itemId, String ownerId) {
        List<CanceledReminder> result = db.query(
                "select r.id,r.status,r.version from reminders r where r.schedule_item_id=? and r.owner_id=?",
                (rs, n) -> new CanceledReminder((UUID) rs.getObject("id"), rs.getString("status"), rs.getLong("version")),
                itemId, ownerId);
        List<CanceledReminder> canceled = new ArrayList<>();
        for (CanceledReminder reminder : result) {
            if (!List.of(CANCELLABLE_REMINDER_STATUSES).contains(reminder.status())) continue;
            long nextVersion = reminder.version() + 1;
            int updated = db.update("update reminders set status='CANCELLED',updated_at=?,version=? where id=? and owner_id=? and status=? and version=?",
                    OffsetDateTime.now(clock), nextVersion, reminder.id(), ownerId, reminder.status(), reminder.version());
            if (updated != 1) throw conflict("reminder version changed while cancelling");
            db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload) values(?,?,?,?,?,?,?)",
                    UUID.randomUUID(), reminder.id(), "DELETE", nextVersion, nextVersion, OffsetDateTime.now(clock),
                    payload(reminder.id(), nextVersion));
            canceled.add(new CanceledReminder(reminder.id(), "CANCELLED", nextVersion));
        }
        return canceled;
    }

    private DayPlanDraft readDraft(UUID planId, String ownerId) {
        String json = db.queryForObject("select draft_json from day_plans where id=? and owner_id=?", String.class, planId, ownerId);
        if (json == null || json.isBlank()) throw conflict("day plan has no editable draft context");
        try {
            return mapper.readValue(json, DayPlanDraft.class);
        } catch (JsonProcessingException e) {
            throw conflict("stored day plan draft is invalid");
        }
    }

    private String draftJson(DayPlanDraft draft) {
        try {
            return mapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize revised day plan draft", e);
        }
    }

    private String payload(UUID reminderId, long schedulerVersion) {
        try {
            return mapper.writeValueAsString(Map.of("reminderId", reminderId.toString(),
                    "schedulerVersion", schedulerVersion, "idempotencyKey", reminderId + ":" + schedulerVersion));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseStatusException badRequest(String message) { return error(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return error(HttpStatus.CONFLICT, message); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    public record RevisionResult(DayPlan plan, DayPlanPreviewService.PlanPreview preview,
                                 List<UUID> canceledReminderIds, int removedSequence) {
        public RevisionResult {
            canceledReminderIds = List.copyOf(canceledReminderIds);
        }
    }

    private record CanceledReminder(UUID id, String status, long version) {}
}
