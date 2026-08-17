package com.middleproject.reminder.transport.domain;

public record SubwayScheduleItem(
        String stationId,
        String destinationStationName,
        String trainNo,
        String departureTime,
        String arrivalTime,
        String dailyTypeCode,
        String upDownTypeCode
) {}
