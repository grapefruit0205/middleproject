package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.domain.HandoffLinks;
import com.middleproject.reminder.transport.domain.TransportMode;
import com.middleproject.reminder.transport.domain.TransportOption;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.domain.TransportProvenance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TransportDomainTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    void transportOptionEnforcesSeoulTimeAndDoesNotFabricateAbsentFields() {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime departure = OffsetDateTime.now(SEOUL_ZONE).plusMinutes(15);
        OffsetDateTime expires = OffsetDateTime.now(SEOUL_ZONE).plusSeconds(60);

        TransportProvenance provenance = new TransportProvenance(
                "TAGO",
                "apis.data.go.kr/1613000/SubwayInfo",
                false, // scheduled, not realtime
                OffsetDateTime.now(SEOUL_ZONE)
        );

        TransportOption option = TransportOption.builder()
                .id("subway-sched-123")
                .mode(TransportMode.SUBWAY)
                .originName("Gangnam")
                .destinationName("Pangyo")
                .routeLabel("Shinbundang")
                .departureTime(departure)
                .arrivalTime(null) // intentionally absent, not fabricated
                .estimatedDuration(null) // absent
                .transferCount(0)
                .priceKrw(null) // absent
                .provenance(provenance)
                .fetchedAt(OffsetDateTime.now(SEOUL_ZONE))
                .expiresAt(expires)
                .officialBookingUrl(null)
                .build();

        assertEquals("subway-sched-123", option.id());
        assertEquals(TransportMode.SUBWAY, option.mode());
        assertEquals("Gangnam", option.originName());
        assertEquals("Pangyo", option.destinationName());
        assertEquals("Shinbundang", option.routeLabel());
        assertEquals(departure, option.departureTime());
        assertNull(option.arrivalTime());
        assertNull(option.estimatedDuration());
        assertEquals(0, option.transferCount());
        assertNull(option.priceKrw());
        assertFalse(option.provenance().realtime());
        assertEquals("TAGO", option.provenance().sourceName());
        assertEquals(expires, option.expiresAt());
        assertNull(option.officialBookingUrl());
    }

    @Test
    void handoffLinksAreVerifiedHttpsOnly() {
        assertEquals("https://www.letskorail.com/", HandoffLinks.KORAIL);
        assertEquals("https://etk.srail.kr/", HandoffLinks.SRT);
        assertEquals("https://www.kobus.co.kr/", HandoffLinks.EXPRESS_BUS);
        assertEquals("https://txbus.t-money.co.kr/", HandoffLinks.TMONEY_INTERCITY_BUS);

        assertTrue(HandoffLinks.isAllowlisted("https://www.letskorail.com/"));
        assertTrue(HandoffLinks.isAllowlisted("https://etk.srail.kr/"));
        assertTrue(HandoffLinks.isAllowlisted("https://www.kobus.co.kr/"));
        assertTrue(HandoffLinks.isAllowlisted("https://txbus.t-money.co.kr/"));
        assertFalse(HandoffLinks.isAllowlisted("http://www.letskorail.com/"));
        assertFalse(HandoffLinks.isAllowlisted("korail://app/booking"));
        assertFalse(HandoffLinks.isAllowlisted("https://unapproved.example.com"));
    }

    @Test
    void typedTransportOutcomesPreserveFailureCategories() {
        TransportOutcome<String> success = TransportOutcome.success("data");
        assertTrue(success.isSuccess());
        assertEquals("data", success.value());

        TransportOutcome<String> timeout = TransportOutcome.timeout("Request timed out after 5000ms");
        assertTrue(timeout.isFailure());
        assertEquals(TransportOutcome.FailureKind.TIMEOUT, timeout.failureKind());

        TransportOutcome<String> rateLimited = TransportOutcome.rateLimited("HTTP 429 too many requests");
        assertTrue(rateLimited.isFailure());
        assertEquals(TransportOutcome.FailureKind.RATE_LIMITED, rateLimited.failureKind());

        TransportOutcome<String> authRejected = TransportOutcome.authRejected("Invalid service key");
        assertTrue(authRejected.isFailure());
        assertEquals(TransportOutcome.FailureKind.AUTH_REJECTED, authRejected.failureKind());

        TransportOutcome<String> malformed = TransportOutcome.malformed("Unexpected XML root element");
        assertTrue(malformed.isFailure());
        assertEquals(TransportOutcome.FailureKind.MALFORMED, malformed.failureKind());

        TransportOutcome<String> empty = TransportOutcome.empty();
        assertTrue(empty.isEmpty());

        TransportOutcome<String> disabled = TransportOutcome.disabledInsecure("Seoul subway HTTP endpoint is disabled by default");
        assertTrue(disabled.isFailure());
        assertEquals(TransportOutcome.FailureKind.DISABLED_INSECURE, disabled.failureKind());
    }
}
