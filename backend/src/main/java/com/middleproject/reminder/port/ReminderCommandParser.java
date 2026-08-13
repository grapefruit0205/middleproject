package com.middleproject.reminder.port;

import com.middleproject.reminder.application.ReminderCommand;

import java.time.LocalDate;
import java.util.List;

public interface ReminderCommandParser {
    ParseResult parse(String text, LocalDate referenceDate);

    record ParseResult(Status status, ReminderCommand command, List<String> issues, boolean confirmationRequired) {
        public ParseResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public enum Status { PARSED, AMBIGUOUS, PARSER_FAILED, BUSINESS_INVALID }

        public static ParseResult parserFailed(String issue) {
            return new ParseResult(Status.PARSER_FAILED, null, List.of(issue), false);
        }

        public static ParseResult businessInvalid(List<String> issues) {
            return new ParseResult(Status.BUSINESS_INVALID, null, issues, false);
        }

        public static ParseResult ambiguous(ReminderCommand command, String issue) {
            return new ParseResult(Status.AMBIGUOUS, command, List.of(issue), true);
        }
    }
}
