package com.middleproject.reminder.transport.domain;

public record ExpressBusArrival(
        String routeName,
        String busGrade,
        String departureTerminalName,
        String arrivalTerminalName,
        Integer remainingMinutes,
        String departurePlannedTime
) {}
