package com.middleproject.reminder.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Mutable test clock; production beans and code see the same instant until advanced. */
public final class AdjustableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public AdjustableClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() { return zone; }

    @Override
    public Clock withZone(ZoneId zone) { return new AdjustableClock(instant, zone); }

    @Override
    public Instant instant() { return instant; }

    public void advance(Duration duration) { instant = instant.plus(duration); }

    public void set(Instant newInstant) { instant = newInstant; }
}
