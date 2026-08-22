package com.middleproject.reminder;

import com.middleproject.reminder.application.DayPlanConfirmationService;
import com.middleproject.reminder.application.DayPlanRevisionService;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:day-plan-revision;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class DayPlanRevisionServiceIntegrationTest {
    @Autowired DayPlanConfirmationService confirmations;
    @Autowired DayPlanRevisionService revisions;
    @Autowired JdbcTemplate db;

    @Test
    void cancelingAnItemCancelsOldNotificationsRebuildsRemainingTimelineAndReplaysIdempotently() {
        LocalDate date = LocalDate.of(2040, 4, 1);
        var first = confirmations.confirm(draft(date, true), "confirm-revision-1", "confirm-revision-key");
        long currentVersion = first.plan().version();

        var revised = revisions.cancelItem(first.plan().id(), 0, currentVersion, "revision-cancel-key");
        var replay = revisions.cancelItem(first.plan().id(), 0, currentVersion, "revision-cancel-key");

        assertEquals(first.plan().id(), revised.plan().id());
        assertEquals(revised.plan().id(), replay.plan().id());
        assertEquals(currentVersion + 1, revised.plan().version());
        assertEquals(2, db.queryForObject("select count(*) from schedule_items where day_plan_id=?", Integer.class, first.plan().id()));
        assertEquals(1, db.queryForObject("select count(*) from schedule_items where day_plan_id=? and status='CANCELLED'", Integer.class, first.plan().id()));
        assertEquals(1, db.queryForObject("select count(*) from travel_legs where day_plan_id=?", Integer.class, first.plan().id()));
        assertEquals(2, db.queryForObject("select count(*) from reminders where owner_id=?", Integer.class, "demo-owner"));
        assertEquals(1, db.queryForObject("select count(*) from reminders where owner_id=? and status='CANCELLED'", Integer.class, "demo-owner"));
        UUID canceledReminder = db.queryForObject("select id from reminders where owner_id=? and status='CANCELLED'", UUID.class, "demo-owner");
        assertEquals(1, db.queryForObject("select count(*) from reminders where id=? and schedule_item_id is not null", Integer.class, canceledReminder));
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox where reminder_id=? and operation='DELETE'", Integer.class, canceledReminder));
        assertEquals(1, db.queryForObject("select count(*) from reminders where owner_id=? and status='SCHEDULE_PENDING'", Integer.class, "demo-owner"));
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox where operation='UPSERT' and reminder_id in (select id from reminders where status='SCHEDULE_PENDING')", Integer.class));
    }

    @Test
    void rejectsStalePlanVersionBeforeChangingNotifications() {
        LocalDate date = LocalDate.of(2040, 5, 1);
        var first = confirmations.confirm(draft(date, true), "confirm-revision-2", "confirm-revision-key-2");
        assertThrows(RuntimeException.class,
                () -> revisions.cancelItem(first.plan().id(), 0, first.plan().version() - 1, "revision-stale-key"));
        assertEquals(2, db.queryForObject("select count(*) from schedule_items where day_plan_id=?", Integer.class, first.plan().id()));
    }

    private static DayPlanDraft draft(LocalDate date, boolean twoItems) {
        OffsetDateTime first = date.atTime(9, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        OffsetDateTime second = date.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        var items = new java.util.ArrayList<ScheduleDraftItem>();
        items.add(new ScheduleDraftItem("병원", ScheduleTimeType.FIXED_START, first,
                first.plusMinutes(60), 60, "병원", "서울", new GeoPoint(37.51, 127.01), "자차"));
        if (twoItems) items.add(new ScheduleDraftItem("점심", ScheduleTimeType.FIXED_START, second,
                second.plusMinutes(60), 60, "식당", "서울", new GeoPoint(37.52, 127.02), "자차"));
        return new DayPlanDraft(date, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0), items, 15, false);
    }
}
