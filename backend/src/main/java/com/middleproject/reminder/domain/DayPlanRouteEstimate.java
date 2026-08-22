package com.middleproject.reminder.domain;

/** Read-only provider result. It is never a booking, reservation, or guarantee. */
public record DayPlanRouteEstimate(int durationMinutes, String provider, String source, String handoffUrl) {
    public DayPlanRouteEstimate {
        if (durationMinutes < 0) throw new IllegalArgumentException("durationMinutes must be nonnegative");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        if (handoffUrl != null && !handoffUrl.isBlank()
                && !(handoffUrl.startsWith("https://") || handoffUrl.startsWith("http://"))) {
            throw new IllegalArgumentException("handoffUrl must be an HTTP(S) URL");
        }
    }
}
