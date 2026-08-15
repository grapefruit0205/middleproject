package com.middleproject.reminder.support;

import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Builds a DRAFT trip with complete private-car input (Seoul -> Busan, 2030-01-01T10:00+09:00). */
public final class PrivateCarFixtures {

    public static final OffsetDateTime DEPART = OffsetDateTime.parse("2030-01-01T10:00:00+09:00");
    public static final OffsetDateTime RETURN = OffsetDateTime.parse("2030-01-03T18:00:00+09:00");
    public static final String ORIGIN = "Seoul";
    public static final String DESTINATION = "Busan";

    private PrivateCarFixtures() {}

    public static UUID draftTrip(TripService trips) {
        return trips.createDraft(ORIGIN, DESTINATION, DEPART, RETURN,
                "private-car-draft-" + UUID.randomUUID()).id();
    }

    public static UUID readyDraft(TripService trips) {
        UUID id = draftTrip(trips);
        trips.answerQuestion(id, PrivateCarPlanningInput.LEAD_KEY, "30", "lead-" + UUID.randomUUID());
        return id;
    }

    public static String leadAnswer(PrivateCarPlanningService service, UUID tripId) {
        return "private-car-lead-" + UUID.randomUUID();
    }
}
