package com.middleproject.reminder.web;

import com.middleproject.reminder.port.ReminderCommandParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;


@RestController
@RequestMapping("/api/reminder-commands")
public class ReminderCommandController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ReminderCommandParser parser;
    private final Clock clock;

    public ReminderCommandController(ReminderCommandParser parser, Clock clock) {
        this.parser = parser;
        this.clock = clock.withZone(SEOUL);
    }

    public record Request(String text, LocalDate referenceDate) {}

    @PostMapping("/parse")
    public ResponseEntity<ReminderCommandParser.ParseResult> parse(@RequestBody(required = false) Request request) {
        var result = request == null
                ? ReminderCommandParser.ParseResult.parserFailed("request is required")
                : parser.parse(request.text(), request.referenceDate() == null ? LocalDate.now(clock) : request.referenceDate());
        HttpStatus status = switch (result.status()) {
            case PARSED, AMBIGUOUS -> HttpStatus.OK;
            case PARSER_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case BUSINESS_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(result);
    }
}
