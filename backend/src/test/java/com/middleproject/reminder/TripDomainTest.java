package com.middleproject.reminder;

import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripDomainTest {

    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-01T10:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2030-01-03T18:00:00+09:00");

    private Trip trip(TripStatus status) {
        return new Trip(TRIP_ID, "demo-owner", "Seoul", "Tokyo", START, END,
                status, null, new HashMap<>(), 1);
    }

    @Test
    void draftMustPassThroughAwaitingConfirmationBeforeConfirm() {
        var draft = trip(TripStatus.DRAFT);
        assertThrows(IllegalStateException.class, () -> draft.confirm("1"), "DRAFT cannot confirm directly");
        var awaiting = draft.toAwaitingConfirmation();
        assertEquals(TripStatus.AWAITING_CONFIRMATION, awaiting.status());
        assertEquals(2, awaiting.version());
        assertThrows(IllegalArgumentException.class, () -> awaiting.confirm(null), "confirmation is required");
        assertThrows(IllegalArgumentException.class, () -> awaiting.confirm("  "), "confirmation must be nonblank");
        var confirmed = awaiting.confirm("confirm-1");
        assertEquals(TripStatus.CONFIRMED, confirmed.status());
        assertEquals(3, confirmed.version());
        assertEquals("confirm-1", confirmed.confirmationId());
        assertThrows(IllegalStateException.class, () -> confirmed.confirm("confirm-2"), "CONFIRMED cannot confirm again");
    }

    @Test
    void legalTransitionsCoverEveryStatus() {
        assertTrue(trip(TripStatus.DRAFT).status().canTransitionTo(TripStatus.AWAITING_CONFIRMATION));
        assertTrue(trip(TripStatus.DRAFT).status().canTransitionTo(TripStatus.CANCELLED));
        assertTrue(trip(TripStatus.DRAFT).status().canTransitionTo(TripStatus.EXPIRED));
        assertTrue(trip(TripStatus.AWAITING_CONFIRMATION).status().canTransitionTo(TripStatus.CONFIRMED));
        assertTrue(trip(TripStatus.AWAITING_CONFIRMATION).status().canTransitionTo(TripStatus.CANCELLED));
        assertTrue(trip(TripStatus.AWAITING_CONFIRMATION).status().canTransitionTo(TripStatus.EXPIRED));
        assertTrue(trip(TripStatus.CONFIRMED).status().canTransitionTo(TripStatus.CANCELLED));
        assertTrue(trip(TripStatus.EXPIRED).status().canTransitionTo(TripStatus.DRAFT));
        assertTrue(trip(TripStatus.CANCELLED).status().canTransitionTo(TripStatus.DRAFT));
        assertFalse(trip(TripStatus.CONFIRMED).status().canTransitionTo(TripStatus.DRAFT));
    }

    @Test
    void confirmAndAnswerMergeDraftContextAndBumpVersion() {
        var draft = trip(TripStatus.DRAFT);
        var answered = draft.answer("Q1", "A1");
        assertEquals("A1", answered.draftContext().get("Q1"));
        assertEquals(2, answered.version());
        var answeredTwice = answered.answer("Q2", "A2");
        assertEquals("A1", answeredTwice.draftContext().get("Q1"));
        assertEquals("A2", answeredTwice.draftContext().get("Q2"));
        assertThrows(IllegalArgumentException.class, () -> draft.answer("", "A"));
        assertThrows(IllegalArgumentException.class, () -> draft.answer("Q", null));
        assertThrows(IllegalStateException.class, () -> trip(TripStatus.CONFIRMED).answer("Q3", "A3"));
    }

    @Test
    void cancelExpireAndRestartFollowTheStateMachine() {
        assertEquals(TripStatus.CANCELLED, trip(TripStatus.DRAFT).cancel().status());
        assertEquals(TripStatus.CANCELLED, trip(TripStatus.AWAITING_CONFIRMATION).cancel().status());
        assertEquals(TripStatus.CANCELLED, trip(TripStatus.CONFIRMED).cancel().status());
        assertThrows(IllegalStateException.class, () -> trip(TripStatus.CANCELLED).cancel());

        assertEquals(TripStatus.EXPIRED, trip(TripStatus.DRAFT).expire().status());
        assertEquals(TripStatus.EXPIRED, trip(TripStatus.AWAITING_CONFIRMATION).expire().status());
        assertThrows(IllegalStateException.class, () -> trip(TripStatus.CONFIRMED).expire());

        assertEquals(TripStatus.DRAFT, trip(TripStatus.EXPIRED).restart().status());
        assertEquals(TripStatus.DRAFT, trip(TripStatus.CANCELLED).restart().status());
        assertThrows(IllegalStateException.class, () -> trip(TripStatus.DRAFT).restart());
    }
}
