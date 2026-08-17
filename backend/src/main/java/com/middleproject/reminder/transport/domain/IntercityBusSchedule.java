package com.middleproject.reminder.transport.domain;

public record IntercityBusSchedule(
        String departureTerminalName,
        String arrivalTerminalName,
        String departurePlannedTime,
        String arrivalPlannedTime,
        String busGrade,
        Integer chargeKrw
) {}
