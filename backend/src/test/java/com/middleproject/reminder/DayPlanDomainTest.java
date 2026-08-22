package com.middleproject.reminder;

import com.middleproject.reminder.domain.DayPlan;
import com.middleproject.reminder.domain.DayPlanStatus;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleItem;
import com.middleproject.reminder.domain.ScheduleItemStatus;
import com.middleproject.reminder.domain.ScheduleTimeType;
import com.middleproject.reminder.domain.TravelLeg;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DayPlanDomainTest {
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID NEXT_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-01T09:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2030-01-01T10:00:00+09:00");

    @Test
    void dayPlanRequiresValidOwnerDateTimezoneAndLegalTransitions() {
        var draft = new DayPlan(PLAN_ID, "demo-owner", LocalDate.of(2030, 1, 1), "Asia/Seoul", DayPlanStatus.DRAFT, 0);
        var proposed = draft.propose();
        var confirmed = proposed.confirm();

        assertEquals(DayPlanStatus.PROPOSED, proposed.status());
        assertEquals(1, proposed.version());
        assertEquals(DayPlanStatus.CONFIRMED, confirmed.status());
        assertEquals(2, confirmed.version());
        assertThrows(IllegalStateException.class, draft::confirm);
        assertThrows(IllegalArgumentException.class, () -> new DayPlan(PLAN_ID, " ", draft.planDate(), "Asia/Seoul", DayPlanStatus.DRAFT, 0));
        assertThrows(IllegalArgumentException.class, () -> new DayPlan(PLAN_ID, "owner", draft.planDate(), "Not/AZone", DayPlanStatus.DRAFT, 0));
    }

    @Test
    void scheduleItemValidatesTimeSemanticsAndSupportsCancellation() {
        var item = new ScheduleItem(ITEM_ID, PLAN_ID, "병원", ScheduleTimeType.FIXED_START,
                START, END, 60, "서울중앙병원", "서울시", new GeoPoint(37.5, 127.0), 0,
                ScheduleItemStatus.PLANNED, 0);

        assertEquals(ScheduleItemStatus.CANCELLED, item.cancel().status());
        assertEquals(1, item.cancel().version());
        assertThrows(IllegalArgumentException.class, () -> new ScheduleItem(ITEM_ID, PLAN_ID, "병원", ScheduleTimeType.FIXED_START,
                END, START, 60, "병원", "주소", null, 0, ScheduleItemStatus.PLANNED, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScheduleItem(ITEM_ID, PLAN_ID, "", ScheduleTimeType.FIXED_START,
                START, END, 60, "병원", "주소", null, 0, ScheduleItemStatus.PLANNED, 0));
    }

    @Test
    void flexibleItemMayOmitStartButFixedItemMayNot() {
        var flexible = new ScheduleItem(ITEM_ID, PLAN_ID, "서점", ScheduleTimeType.FLEXIBLE,
                null, null, 30, "서점", "주소", null, 1, ScheduleItemStatus.PLANNED, 0);
        assertEquals(ScheduleTimeType.FLEXIBLE, flexible.timeType());
        assertThrows(IllegalArgumentException.class, () -> new ScheduleItem(ITEM_ID, PLAN_ID, "병원", ScheduleTimeType.FIXED_START,
                null, END, 60, "병원", "주소", null, 0, ScheduleItemStatus.PLANNED, 0));
    }

    @Test
    void travelLegRequiresOrderedTimesAndProviderProvenance() {
        var leg = new TravelLeg(UUID.randomUUID(), PLAN_ID, ITEM_ID, NEXT_ITEM_ID, "SUBWAY", 35, 10,
                START, END, "provider", "source", Instant.parse("2030-01-01T00:00:00Z"), 0, 0);
        assertEquals(35, leg.durationMinutes());
        assertThrows(IllegalArgumentException.class, () -> new TravelLeg(UUID.randomUUID(), PLAN_ID, ITEM_ID, NEXT_ITEM_ID,
                "SUBWAY", 35, 10, END, START, "provider", "source", Instant.now(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TravelLeg(UUID.randomUUID(), PLAN_ID, ITEM_ID, NEXT_ITEM_ID,
                " ", 35, 10, START, END, "provider", "source", Instant.now(), 0, 0));
    }
}
