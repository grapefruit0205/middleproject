package com.middleproject.reminder;

import com.middleproject.reminder.application.DayPlanValidationService;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayPlanValidationServiceTest {
    private final DayPlanValidationService service = new DayPlanValidationService();
    private static final LocalDate DATE = LocalDate.of(2030, 1, 1);

    @Test
    void asksOnlyForTheFirstMissingField() {
        var empty = service.validate(new DayPlanDraft(null, "Asia/Seoul", null, null, null, List.of(), null, false));
        assertEquals(DayPlanValidationService.QuestionField.PLAN_DATE, empty.nextQuestion().field());

        var noOrigin = service.validate(new DayPlanDraft(DATE, "Asia/Seoul", null, null, null, List.of(), null, false));
        assertEquals(DayPlanValidationService.QuestionField.ORIGIN, noOrigin.nextQuestion().field());

        var noItems = service.validate(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, null, List.of(), null, false));
        assertEquals(DayPlanValidationService.QuestionField.FIRST_ITEM, noItems.nextQuestion().field());
        assertEquals(1, noItems.questions().size());
    }

    @Test
    void reportsMissingItemDetailsInConversationOrder() {
        var item = new ScheduleDraftItem(null, ScheduleTimeType.FIXED_START, null, null,
                null, null, null, null, null);
        var result = service.validate(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, null, List.of(item), null, false));

        assertFalse(result.valid());
        assertEquals(DayPlanValidationService.QuestionField.ITEM_TITLE, result.nextQuestion().field());
        assertEquals("items[0].title", result.nextQuestion().path());
        assertEquals(1, result.questions().size());
    }

    @Test
    void acceptsCompleteDraftAndKeepsOnlyOneTimezoneAwareDay() {
        var first = new ScheduleDraftItem("병원", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T09:00:00+09:00"),
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"), 60,
                "강남세브란스병원", "서울", null, "SUBWAY");
        var second = new ScheduleDraftItem("뮤지컬", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T16:00:00+09:00"),
                OffsetDateTime.parse("2030-01-01T19:00:00+09:00"), 180,
                "예술의전당", "서울", null, "SUBWAY");

        var result = service.validate(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, null,
                List.of(first, second), 15, true));

        assertTrue(result.valid());
        assertEquals(0, result.questions().size());
        assertNotNull(result.orderedItems());
        assertEquals(2, result.orderedItems().size());
    }

    @Test
    void rejectsOverlappingFixedItemsAndCrossDateItems() {
        var first = new ScheduleDraftItem("병원", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T09:00:00+09:00"),
                OffsetDateTime.parse("2030-01-01T10:30:00+09:00"), 90,
                "병원", null, null, "SUBWAY");
        var overlap = new ScheduleDraftItem("약속", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-01T10:00:00+09:00"),
                OffsetDateTime.parse("2030-01-01T11:00:00+09:00"), 60,
                "식당", null, null, "SUBWAY");
        var crossDate = new ScheduleDraftItem("귀가", ScheduleTimeType.FIXED_START,
                OffsetDateTime.parse("2030-01-02T00:10:00+09:00"),
                OffsetDateTime.parse("2030-01-02T01:00:00+09:00"), 50,
                "집", null, null, "SUBWAY");

        var result = service.validate(new DayPlanDraft(DATE, "Asia/Seoul", "집", null, null,
                List.of(first, overlap, crossDate), 15, false));

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("SCHEDULE_OVERLAP")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("ITEM_DATE_MISMATCH")));
    }

    @Test
    void rejectsInvalidNotificationLeadAndTimezoneWithoutWritingAnything() {
        var result = service.validate(new DayPlanDraft(DATE, "Not/AZone", "집", null, null,
                List.of(), 0, false));

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("TIMEZONE_INVALID")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("NOTIFICATION_LEAD_INVALID")));
    }
}
