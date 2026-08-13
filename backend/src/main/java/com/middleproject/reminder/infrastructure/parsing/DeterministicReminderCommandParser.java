package com.middleproject.reminder.infrastructure.parsing;

import com.middleproject.reminder.application.ReminderCommand;
import com.middleproject.reminder.application.ReminderCommandSchemaValidator;
import com.middleproject.reminder.port.ReminderCommandParser;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeterministicReminderCommandParser implements ReminderCommandParser {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern DATE = Pattern.compile("\\b(20\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})\\b");
    private static final Pattern TIME = Pattern.compile("(?<![\\d./-])(?<!\\d)(2[0-3]|1[0-9]|[0-9])(?::([0-5]\\d))?\\s*(am|pm|오전|오후)?\\s*(시)?(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_TIME = Pattern.compile("(?<![\\d./-])(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    private final ReminderCommandSchemaValidator schemaValidator;

    public DeterministicReminderCommandParser() {
        schemaValidator = new ReminderCommandSchemaValidator();
    }

    @Override
    public ParseResult parse(String text, LocalDate referenceDate) {
        if (text == null || text.isBlank()) return ParseResult.parserFailed("input is blank");
        if (referenceDate == null) return ParseResult.parserFailed("reference date is required");
        String normalized = text.replaceAll("오전\\s*(\\d+)", "$1 am").replaceAll("오후\\s*(\\d+)", "$1 pm").replace("오전", "am").replace("오후", "pm");
        Matcher dateMatcher = DATE.matcher(normalized);
        LocalDate date = referenceDate;
        boolean dateAmbiguous = false;
        if (dateMatcher.find()) {
            try {
                date = LocalDate.of(Integer.parseInt(dateMatcher.group(1)), Integer.parseInt(dateMatcher.group(2)), Integer.parseInt(dateMatcher.group(3)));
            } catch (DateTimeException e) {
                return ParseResult.businessInvalid(List.of("invalid date"));
            }
        } else if (text.toLowerCase(Locale.ROOT).contains("tomorrow") || text.contains("내일")) {
            date = referenceDate.plusDays(1);
        } else if (!(text.toLowerCase(Locale.ROOT).contains("today") || text.contains("오늘"))) {
            dateAmbiguous = true;
        }
        Matcher explicitTimeMatcher = EXPLICIT_TIME.matcher(normalized);
        boolean hasExplicitTime = explicitTimeMatcher.find();
        int explicitTimeStart = -1;
        if (hasExplicitTime) {
            explicitTimeStart = explicitTimeMatcher.start();
            int explicitHour = Integer.parseInt(explicitTimeMatcher.group(1));
            int explicitMinute = Integer.parseInt(explicitTimeMatcher.group(2));
            if (explicitHour > 23 || explicitMinute > 59) return ParseResult.businessInvalid(List.of("invalid time"));
        }
        List<TimeCandidate> candidates = new ArrayList<>();
        Matcher timeMatcher = TIME.matcher(normalized);
        while (timeMatcher.find()) {
            candidates.add(new TimeCandidate(timeMatcher.start(), timeMatcher.group(1), timeMatcher.group(2),
                    timeMatcher.group(3), timeMatcher.group(4), isQualifiedTime(normalized, timeMatcher)));
        }
        TimeCandidate selected = candidates.stream().filter(TimeCandidate::qualified).findFirst().orElse(null);
        if (selected == null) return ParseResult.parserFailed("time is required");
        if (hasExplicitTime && selected.start() != explicitTimeStart) {
            for (TimeCandidate candidate : candidates) {
                if (candidate.start() == explicitTimeStart) {
                    selected = candidate;
                    break;
                }
            }
        }
        int hour = Integer.parseInt(selected.hour());
        int minute = selected.minute() == null ? 0 : Integer.parseInt(selected.minute());
        String marker = selected.meridiem() == null ? "" : selected.meridiem().toLowerCase(Locale.ROOT);
        if (marker.isBlank() && selected.koreanHour() != null) marker = selected.koreanHour();
        boolean twelveHour = marker.equals("am") || marker.equals("pm") || marker.equals("오전") || marker.equals("오후");
        if (twelveHour && (hour < 1 || hour > 12)) return ParseResult.businessInvalid(List.of("invalid 12-hour time"));
        if ((marker.equals("pm") || marker.equals("오후")) && hour < 12) hour += 12;
        if ((marker.equals("am") || marker.equals("오전")) && hour == 12) hour = 0;
        if (!twelveHour && hour > 23) return ParseResult.businessInvalid(List.of("invalid time"));
        String title = normalized.substring(0, selected.start()).replaceFirst("(?i)^(remind me to|알림|알려줘)\\s*", "").trim();
        title = title.replaceAll("(?i)\\b(today|tomorrow|on|at)\\b", "").replaceAll("오늘|내일", "").replaceAll("\\b20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2}\\b", "").replaceAll("[ ,]+$", "").trim();
        if (title.isBlank()) return ParseResult.businessInvalid(List.of("title is required"));
        ReminderCommand command = command(title, date.atTime(hour, minute), dateAmbiguous, dateAmbiguous ? "date is not explicit" : null);
        ParseResult validated = validate(command);
        if (validated.status() == ParseResult.Status.BUSINESS_INVALID) return validated;
        if (dateAmbiguous) return ParseResult.ambiguous(command, "date is not explicit");
        return validated;
    }

    private boolean isQualifiedTime(String text, Matcher matcher) {
        if (matcher.group(2) != null || matcher.group(3) != null || matcher.group(4) != null) return true;
        String prefix = text.substring(0, matcher.start()).toLowerCase(Locale.ROOT);
        return prefix.matches("(?s).*\\b(at|오전|오후)\\s*$");
    }

    private record TimeCandidate(int start, String hour, String minute, String meridiem, String koreanHour, boolean qualified) {}

    private ReminderCommand command(String title, LocalDateTime time, boolean confirmation, String reason) {
        return new ReminderCommand(title, time.atZone(SEOUL).toOffsetDateTime(), SEOUL.getId(), confirmation, reason);
    }

    private ParseResult validate(ReminderCommand command) {
        try {
            var errors = schemaValidator.validate(command);
            return errors.isEmpty() ? new ParseResult(ParseResult.Status.PARSED, command, List.of(), false) : ParseResult.businessInvalid(errors);
        } catch (IllegalArgumentException e) {
            return ParseResult.businessInvalid(List.of(e.getMessage()));
        }
    }
}
