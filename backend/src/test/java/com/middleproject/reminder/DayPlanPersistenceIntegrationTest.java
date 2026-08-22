package com.middleproject.reminder;

import com.middleproject.reminder.domain.DayPlan;
import com.middleproject.reminder.domain.DayPlanStatus;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleItem;
import com.middleproject.reminder.domain.ScheduleItemStatus;
import com.middleproject.reminder.domain.ScheduleTimeType;
import com.middleproject.reminder.domain.TravelLeg;
import com.middleproject.reminder.port.DayPlanRepository;
import com.middleproject.reminder.port.ScheduleItemRepository;
import com.middleproject.reminder.port.TravelLegRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:day-plan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
class DayPlanPersistenceIntegrationTest {
    @Autowired DayPlanRepository dayPlans;
    @Autowired ScheduleItemRepository items;
    @Autowired TravelLegRepository legs;
    @Autowired JdbcTemplate db;

    @Test
    void persistsPlanItemsAndLegsOnlyWithinOwnerScope() {
        var planId = UUID.randomUUID();
        var plan = new DayPlan(planId, "owner-a", LocalDate.of(2030, 1, 1), "Asia/Seoul", DayPlanStatus.DRAFT, 0);
        dayPlans.insert(plan);
        var itemId = UUID.randomUUID();
        var item = new ScheduleItem(itemId, planId, "병원", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T09:00:00+09:00"), OffsetDateTime.parse("2030-01-01T10:00:00+09:00"),
                60, "병원", "서울", new GeoPoint(37.5, 127.0), 0, ScheduleItemStatus.PLANNED, 0);
        items.insert(item, "owner-a");
        var nextId = UUID.randomUUID();
        var next = new ScheduleItem(nextId, planId, "약속", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T11:00:00+09:00"), OffsetDateTime.parse("2030-01-01T12:00:00+09:00"),
                60, "식당", "서울", null, 1, ScheduleItemStatus.PLANNED, 0);
        items.insert(next, "owner-a");
        legs.insert(new TravelLeg(UUID.randomUUID(), planId, itemId, nextId, "SUBWAY", 35, 10,
                OffsetDateTime.parse("2030-01-01T10:10:00+09:00"), OffsetDateTime.parse("2030-01-01T10:45:00+09:00"),
                "provider", "source", Instant.parse("2030-01-01T00:00:00Z"), 0, 0), "owner-a");

        assertTrue(dayPlans.findByIdForOwner(planId, "owner-a").isPresent());
        assertTrue(dayPlans.findByIdForOwner(planId, "owner-b").isEmpty());
        assertEquals(2, items.findAllByPlanForOwner(planId, "owner-a").size());
        assertTrue(items.findAllByPlanForOwner(planId, "owner-b").isEmpty());
        assertEquals(1, legs.findAllByPlanForOwner(planId, "owner-a").size());
        assertTrue(legs.findAllByPlanForOwner(planId, "owner-b").isEmpty());
        assertTrue(dayPlans.transition(planId, "owner-a", DayPlanStatus.DRAFT, DayPlanStatus.PROPOSED, 0));
        assertTrue(!dayPlans.transition(planId, "owner-a", DayPlanStatus.DRAFT, DayPlanStatus.PROPOSED, 0));
        assertEquals(DayPlanStatus.PROPOSED, dayPlans.findByIdForOwner(planId, "owner-a").orElseThrow().status());
        assertTrue(items.transition(itemId, "owner-a", ScheduleItemStatus.PLANNED, ScheduleItemStatus.CANCELLED, 0));
        assertTrue(!items.transition(itemId, "owner-a", ScheduleItemStatus.PLANNED, ScheduleItemStatus.CANCELLED, 0));
        assertEquals(ScheduleItemStatus.CANCELLED, items.findByIdForOwner(itemId, "owner-a").orElseThrow().status());
        assertEquals(1, db.queryForObject("select count(*) from day_plans", Integer.class));
    }
}
