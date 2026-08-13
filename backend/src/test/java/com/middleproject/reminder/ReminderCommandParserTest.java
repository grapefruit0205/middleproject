package com.middleproject.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.ReminderCommandSchemaValidator;
import com.middleproject.reminder.infrastructure.parsing.DeterministicReminderCommandParser;
import com.middleproject.reminder.port.ReminderCommandParser.ParseResult.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReminderCommandParserTest {
    private final DeterministicReminderCommandParser parser = new DeterministicReminderCommandParser();
    private final LocalDate referenceDate = LocalDate.of(2030, 1, 1);

    @Test
    void parsesEnglishFixtureWithSeoulOffset() {
        var result = parser.parse("remind me to call mom tomorrow at 9:30 am", referenceDate);
        assertEquals(Status.PARSED, result.status(), result.issues().toString());
        assertEquals("call mom", result.command().title());
        assertEquals("2030-01-02T09:30+09:00", result.command().scheduledAt().toString());
        assertEquals("Asia/Seoul", result.command().timezone());
    }

    @Test
    void preservesStandaloneNumbersBeforeExplicitTime() {
        var result = parser.parse("remind me to buy 2 tickets tomorrow at 9:00", referenceDate);
        assertEquals(Status.PARSED, result.status(), result.issues().toString());
        assertEquals("buy 2 tickets", result.command().title());
        assertEquals("2030-01-02T09:00+09:00", result.command().scheduledAt().toString());
    }

    @Test
    void doesNotAcceptStandaloneTitleNumberAsTime() {
        var result = parser.parse("remind me to buy 2 tickets tomorrow", referenceDate);
        assertEquals(Status.PARSER_FAILED, result.status());
    }

    @Test
    void parsesIsoDateAndKoreanTimeWithoutTreatingDateAsTime() {
        var result = parser.parse("알림 보고서 2030-12-31 오후 3시", referenceDate);
        assertEquals(Status.PARSED, result.status(), result.issues().toString());
        assertEquals("2030-12-31T15:00+09:00", result.command().scheduledAt().toString());
    }

    @Test
    void marksDateAmbiguityForConfirmation() {
        var result = parser.parse("remind me to call mom at 9 am", referenceDate);
        assertEquals(Status.AMBIGUOUS, result.status());
        assertTrue(result.confirmationRequired());
        assertTrue(result.issues().get(0).contains("date"));
        assertEquals("2030-01-01T09:00+09:00", result.command().scheduledAt().toString());
    }

    @Test
    void rejectsInvalidMinutes() {
        assertEquals(Status.BUSINESS_INVALID, parser.parse("remind me to call mom at 9:99", referenceDate).status());
    }

    @Test
    void separatesBlankAndUnsupportedParserFailures() {
        assertEquals(Status.PARSER_FAILED, parser.parse("", referenceDate).status());
        assertEquals(Status.PARSER_FAILED, parser.parse("remind me to call mom tomorrow", referenceDate).status());
        assertNull(parser.parse(null, referenceDate).command());
    }

    @Test
    void returnsBusinessInvalidForInvalidDateAndMissingTitle() {
        assertEquals(Status.BUSINESS_INVALID, parser.parse("remind me to call 2030-99-31 at 9 am", referenceDate).status());
        assertEquals(Status.BUSINESS_INVALID, parser.parse("remind me to 2030-01-02 at 9 am", referenceDate).status());
    }

    @Test
    void acceptsValidSchemaFixtureDirectly() throws Exception {
        var node = new ObjectMapper().readTree(getClass().getResourceAsStream("/fixtures/reminder-command-valid.json"));
        assertTrue(new ReminderCommandSchemaValidator().validate(node).isEmpty());
    }

    @Test
    void rejectsInvalidSchemaFixtureDirectly() throws Exception {
        var node = new ObjectMapper().readTree(getClass().getResourceAsStream("/fixtures/reminder-command-invalid.json"));
        assertFalse(new ReminderCommandSchemaValidator().validate(node).isEmpty());
    }

    @Test
    void requiresReferenceDate() {
        assertEquals(Status.PARSER_FAILED, parser.parse("call mom tomorrow at 9 am", null).status());
    }
}
