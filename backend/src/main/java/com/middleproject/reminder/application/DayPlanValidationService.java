package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure validation/question boundary for the day-plan conversation. It never writes to a
 * repository, resolves a place, calls a provider, or schedules a notification.
 */
@Service
public class DayPlanValidationService {
    private static final int MIN_NOTIFICATION_LEAD_MINUTES = 1;
    private static final int MAX_NOTIFICATION_LEAD_MINUTES = 24 * 60;

    public ValidationResult validate(DayPlanDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (draft == null) {
            issues.add(new ValidationIssue("DRAFT_REQUIRED", "day plan draft is required", null));
            return result(issues, null, List.of());
        }

        ZoneId zone = validateHeader(draft, issues);
        if (draft.originName() == null || draft.originName().isBlank()) {
            issues.add(new ValidationIssue("ORIGIN_REQUIRED", "origin is required before planning a route", "originName"));
        }
        if (draft.notificationLeadMinutes() != null
                && (draft.notificationLeadMinutes() < MIN_NOTIFICATION_LEAD_MINUTES
                || draft.notificationLeadMinutes() > MAX_NOTIFICATION_LEAD_MINUTES)) {
            issues.add(new ValidationIssue("NOTIFICATION_LEAD_INVALID",
                    "notification lead must be between 1 and 1440 minutes", "notificationLeadMinutes"));
        }

        if (draft.items().isEmpty()) {
            issues.add(new ValidationIssue("ITEM_REQUIRED", "at least one schedule item is required", "items"));
        }
        for (int i = 0; i < draft.items().size(); i++) {
            validateItem(draft, draft.items().get(i), i, zone, issues);
        }
        validateOverlaps(draft.items(), issues);

        Question question = firstQuestion(draft, zone, issues);
        List<ScheduleDraftItem> ordered = orderItems(draft.items());
        return result(issues, question, ordered);
    }

    private ZoneId validateHeader(DayPlanDraft draft, List<ValidationIssue> issues) {
        if (draft.planDate() == null) {
            issues.add(new ValidationIssue("PLAN_DATE_REQUIRED", "plan date is required", "planDate"));
        }
        try {
            return ZoneId.of(draft.timezone());
        } catch (DateTimeException | NullPointerException e) {
            issues.add(new ValidationIssue("TIMEZONE_INVALID", "timezone must be a valid IANA zone", "timezone"));
            return null;
        }
    }

    private void validateItem(DayPlanDraft draft, ScheduleDraftItem item, int index, ZoneId zone,
                              List<ValidationIssue> issues) {
        String path = "items[" + index + "]";
        if (item == null) {
            issues.add(new ValidationIssue("ITEM_REQUIRED", "schedule item must not be null", path));
            return;
        }
        if (item.title() == null || item.title().isBlank()) {
            issues.add(new ValidationIssue("ITEM_TITLE_REQUIRED", "schedule item title is required", path + ".title"));
        }
        if (item.timeType() == null) {
            issues.add(new ValidationIssue("ITEM_TIME_TYPE_REQUIRED", "schedule item time type is required", path + ".timeType"));
        }
        if (item.timeType() != ScheduleTimeType.FLEXIBLE && item.startsAt() == null) {
            issues.add(new ValidationIssue("ITEM_START_REQUIRED", "fixed or deadline item needs a start time", path + ".startsAt"));
        }
        if (item.startsAt() != null && item.endsAt() != null && item.endsAt().isBefore(item.startsAt())) {
            issues.add(new ValidationIssue("ITEM_TIME_ORDER", "item end must not be before item start", path));
        }
        if (item.durationMinutes() != null && item.durationMinutes() < 0) {
            issues.add(new ValidationIssue("ITEM_DURATION_INVALID", "item duration must be nonnegative", path + ".durationMinutes"));
        }
        if (item.timeType() != ScheduleTimeType.FLEXIBLE && item.endsAt() == null && item.durationMinutes() == null) {
            issues.add(new ValidationIssue("ITEM_DURATION_REQUIRED", "fixed item needs an end time or duration", path + ".durationMinutes"));
        }
        if (item.placeName() == null || item.placeName().isBlank()) {
            issues.add(new ValidationIssue("ITEM_PLACE_REQUIRED", "schedule item place is required", path + ".placeName"));
        }
        if (item.travelMode() == null || item.travelMode().isBlank()) {
            issues.add(new ValidationIssue("ITEM_TRAVEL_MODE_REQUIRED", "travel mode is required for each leg", path + ".travelMode"));
        }
        if (zone != null && draft.planDate() != null) {
            if (item.startsAt() != null && !sameDate(item.startsAt(), draft.planDate(), zone)) {
                issues.add(new ValidationIssue("ITEM_DATE_MISMATCH", "item must remain on the selected plan date", path + ".startsAt"));
            }
            if (item.endsAt() != null && !sameDate(item.endsAt(), draft.planDate(), zone)) {
                issues.add(new ValidationIssue("ITEM_DATE_MISMATCH", "item must remain on the selected plan date", path + ".endsAt"));
            }
        }
    }

    private boolean sameDate(OffsetDateTime time, LocalDate planDate, ZoneId zone) {
        return time.atZoneSameInstant(zone).toLocalDate().equals(planDate);
    }

    private void validateOverlaps(List<ScheduleDraftItem> items, List<ValidationIssue> issues) {
        List<TimedItem> timed = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ScheduleDraftItem item = items.get(i);
            if (item == null || item.startsAt() == null) continue;
            OffsetDateTime end = item.endsAt();
            if (end == null && item.durationMinutes() != null && item.durationMinutes() >= 0) {
                end = item.startsAt().plusMinutes(item.durationMinutes());
            }
            if (end != null && !end.isBefore(item.startsAt())) timed.add(new TimedItem(i, item.startsAt(), end));
        }
        timed.sort(Comparator.comparing(TimedItem::start));
        for (int i = 1; i < timed.size(); i++) {
            TimedItem previous = timed.get(i - 1);
            TimedItem current = timed.get(i);
            if (current.start().isBefore(previous.end())) {
                issues.add(new ValidationIssue("SCHEDULE_OVERLAP",
                        "fixed schedule items overlap", "items[" + current.index() + "]"));
            }
        }
    }

    private Question firstQuestion(DayPlanDraft draft, ZoneId zone, List<ValidationIssue> issues) {
        if (draft.planDate() == null) return new Question(QuestionField.PLAN_DATE, "planDate", "어느 날짜의 일정을 만들까요?");
        if (zone == null) return new Question(QuestionField.TIMEZONE, "timezone", "일정의 시간대가 Asia/Seoul이 맞나요?");
        if (draft.originName() == null || draft.originName().isBlank()) {
            return new Question(QuestionField.ORIGIN, "originName", "어디에서 출발하시나요? 집·회사·역·건물 이름으로 알려주세요.");
        }
        if (draft.items().isEmpty()) {
            return new Question(QuestionField.FIRST_ITEM, "items[0]", "오늘(또는 해당 날짜)에 가장 먼저 해야 할 일정은 무엇인가요?");
        }
        for (int i = 0; i < draft.items().size(); i++) {
            ScheduleDraftItem item = draft.items().get(i);
            String path = "items[" + i + "]";
            if (item == null || item.title() == null || item.title().isBlank()) {
                return new Question(QuestionField.ITEM_TITLE, path + ".title", "이 일정의 이름은 무엇인가요?");
            }
            if (item.timeType() == null) {
                return new Question(QuestionField.ITEM_TIME_TYPE, path + ".timeType", "이 일정은 시작 시간이 정해져 있나요, 도착 마감인가요, 유동적인가요?");
            }
            if (item.timeType() != ScheduleTimeType.FLEXIBLE && item.startsAt() == null) {
                return new Question(QuestionField.ITEM_START, path + ".startsAt", "몇 시에 시작하거나 도착해야 하나요?");
            }
            if (item.placeName() == null || item.placeName().isBlank()) {
                return new Question(QuestionField.ITEM_PLACE, path + ".placeName", "어디에서 하는 일정인가요? 장소명이나 주소를 알려주세요.");
            }
            if (item.timeType() != ScheduleTimeType.FLEXIBLE && item.endsAt() == null && item.durationMinutes() == null) {
                return new Question(QuestionField.ITEM_DURATION, path + ".durationMinutes", "이 일정은 몇 분 정도 걸리나요?");
            }
            if (item.travelMode() == null || item.travelMode().isBlank()) {
                return new Question(QuestionField.ITEM_TRAVEL_MODE, path + ".travelMode", "이 장소까지 어떤 교통수단으로 이동하시나요? (지하철·버스·자차 등)");
            }
        }
        return null;
    }

    private List<ScheduleDraftItem> orderItems(List<ScheduleDraftItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(item -> item == null || item.startsAt() == null
                        ? OffsetDateTime.MAX : item.startsAt()))
                .toList();
    }

    private ValidationResult result(List<ValidationIssue> issues, Question question, List<ScheduleDraftItem> ordered) {
        return new ValidationResult(issues.isEmpty(), List.copyOf(issues),
                question == null ? List.of() : List.of(question), question, ordered);
    }

    private record TimedItem(int index, OffsetDateTime start, OffsetDateTime end) {}

    public enum QuestionField {
        PLAN_DATE, TIMEZONE, ORIGIN, FIRST_ITEM, ITEM_TITLE, ITEM_TIME_TYPE, ITEM_START,
        ITEM_PLACE, ITEM_DURATION, ITEM_TRAVEL_MODE
    }

    public record Question(QuestionField field, String path, String prompt) {}

    public record ValidationIssue(String code, String message, String path) {}

    public record ValidationResult(boolean valid, List<ValidationIssue> issues,
                                   List<Question> questions, Question nextQuestion,
                                   List<ScheduleDraftItem> orderedItems) {}
}
