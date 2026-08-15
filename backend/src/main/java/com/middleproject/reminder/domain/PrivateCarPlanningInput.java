package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;

/**
 * Immutable partial input for private-car planning. Missing information is reported by
 * {@link #missingQuestion()} in a stable order: origin, destination, departureAt, lead.
 * The reminder lead is a bounded integer (0..1440 minutes) stored in the namespaced
 * private-car draft value {@value #LEAD_KEY}.
 */
public final class PrivateCarPlanningInput {

    public static final String LEAD_KEY = "private_car.reminder_lead_minutes";
    public static final int LEAD_MIN = 0;
    public static final int LEAD_MAX = 1440;

    private final String origin;
    private final String destination;
    private final OffsetDateTime departureAt;
    private final Integer reminderLeadMinutes;

    private PrivateCarPlanningInput(String origin, String destination, OffsetDateTime departureAt, Integer reminderLeadMinutes) {
        if (reminderLeadMinutes != null && (reminderLeadMinutes < LEAD_MIN || reminderLeadMinutes > LEAD_MAX)) {
            throw new IllegalArgumentException("reminderLeadMinutes must be between " + LEAD_MIN + " and " + LEAD_MAX);
        }
        this.origin = blankToNull(origin);
        this.destination = blankToNull(destination);
        this.departureAt = departureAt;
        this.reminderLeadMinutes = reminderLeadMinutes;
    }

    public static PrivateCarPlanningInput of(String origin, String destination, OffsetDateTime departureAt, Integer reminderLeadMinutes) {
        return new PrivateCarPlanningInput(origin, destination, departureAt, reminderLeadMinutes);
    }

    public static PrivateCarPlanningInput fromDraft(Trip trip, Integer reminderLeadMinutes) {
        return new PrivateCarPlanningInput(trip.departure(), trip.destination(), trip.departureAt(),
                reminderLeadMinutes != null ? reminderLeadMinutes : leadFromDraft(trip));
    }

    private static Integer leadFromDraft(Trip trip) {
        String raw = trip.draftContext().get(LEAD_KEY);
        if (raw == null || raw.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed < LEAD_MIN || parsed > LEAD_MAX ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns the identifier of the first missing question in stable order, or null when complete. */
    public String missingQuestion() {
        if (origin == null) return "origin";
        if (destination == null) return "destination";
        if (departureAt == null) return "departureAt";
        if (reminderLeadMinutes == null) return LEAD_KEY;
        return null;
    }

    public boolean complete() { return missingQuestion() == null; }
    public String origin() { return origin; }
    public String destination() { return destination; }
    public OffsetDateTime departureAt() { return departureAt; }
    public Integer reminderLeadMinutes() { return reminderLeadMinutes; }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
