package com.middleproject.reminder.transport.domain;

public record BusArrival(
        String routeId,
        String routeNo,
        String routeType,
        Integer remainingStops,
        Integer remainingSeconds
) {}
