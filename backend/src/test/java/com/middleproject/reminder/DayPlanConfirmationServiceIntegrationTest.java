package com.middleproject.reminder;

import com.middleproject.reminder.application.DayPlanConfirmationService;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:day-plan-confirm;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class DayPlanConfirmationServiceIntegrationTest {
    @Autowired DayPlanConfirmationService confirmations;
    @Autowired JdbcTemplate db;

    @Test
    void persistsConfirmedPlanNotificationsAndOutboxAtomicallyAndReplaysIdempotently() {
        LocalDate date = LocalDate.of(2040, 2, 1);
        var draft = draft(date);

        var first = confirmations.confirm(draft, "user-confirm-1", "day-plan-confirm-1");
        var replay = confirmations.confirm(draft, "user-confirm-1", "day-plan-confirm-1");

        assertEquals(first.plan().id(), replay.plan().id());
        assertEquals("CONFIRMED", db.queryForObject("select status from day_plans where id=?", String.class, first.plan().id()));
        assertEquals(1, db.queryForObject("select count(*) from day_plans where owner_id=? and plan_date=?", Integer.class, "demo-owner", date));
        assertEquals(1, db.queryForObject("select count(*) from schedule_items where day_plan_id=?", Integer.class, first.plan().id()));
        assertEquals(1, db.queryForObject("select count(*) from travel_legs where day_plan_id=?", Integer.class, first.plan().id()));
        var reminderId = first.reminderIds().getFirst();
        assertEquals(1, db.queryForObject("select count(*) from reminders where id=? and owner_id=? and status='SCHEDULE_PENDING'", Integer.class, reminderId, "demo-owner"));
        assertEquals(1, db.queryForObject("select count(*) from events e join reminders r on r.event_id=e.id where r.id=?", Integer.class, reminderId));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies p join reminders r on r.policy_id=p.id where r.id=?", Integer.class, reminderId));
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox where reminder_id=?", Integer.class, reminderId));
        assertEquals("PUSH", db.queryForObject("select p.channel from notification_policies p join reminders r on r.policy_id=p.id where r.id=?", String.class, reminderId));
        assertTrue(first.preview().wakeAlarmRequested());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyForDifferentConfirmationPayload() {
        var draft = draft(LocalDate.of(2040, 3, 1));
        confirmations.confirm(draft, "user-confirm-1", "day-plan-confirm-conflict");

        assertThrows(ResponseStatusException.class,
                () -> confirmations.confirm(draft, "different-confirmation", "day-plan-confirm-conflict"));
    }

    @Test
    void confirmsFlexibleItemWithoutCreatingAnUnscheduledNotification() {
        LocalDate date = LocalDate.of(2040, 4, 1);
        var flexible = new ScheduleDraftItem("서점 들르기", ScheduleTimeType.FLEXIBLE, null,
                null, 30, "교보문고", "서울", new GeoPoint(37.51, 127.01), "SUBWAY");
        var draft = new DayPlanDraft(date, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0),
                java.util.List.of(flexible), 15, false);

        var result = confirmations.confirm(draft, "flexible-confirm", "flexible-confirm-key");

        assertEquals(1, db.queryForObject("select count(*) from schedule_items where day_plan_id=?", Integer.class, result.plan().id()));
        assertEquals(0, result.reminderIds().size());
        assertEquals(0, db.queryForObject("select count(*) from reminders where owner_id=? and schedule_item_id in (select id from schedule_items where day_plan_id=?)", Integer.class, "demo-owner", result.plan().id()));
        assertEquals(0, db.queryForObject(
                "select count(*) from schedule_outbox o join reminders r on r.id=o.reminder_id "
                        + "where r.schedule_item_id in (select id from schedule_items where day_plan_id=?)",
                Integer.class, result.plan().id()));
    }

    private static DayPlanDraft draft(LocalDate date) {
        OffsetDateTime start = date.atTime(9, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        return new DayPlanDraft(date, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0),
                java.util.List.of(new ScheduleDraftItem("병원", ScheduleTimeType.FIXED_START, start,
                        start.plusMinutes(60), 60, "병원", "서울", new GeoPoint(37.51, 127.01), "자차")), 15, true);
    }
}
