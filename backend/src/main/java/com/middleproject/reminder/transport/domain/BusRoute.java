package com.middleproject.reminder.transport.domain;

public record BusRoute(
        String routeId,
        String routeNo,
        String routeType,
        String startNodeName,
        String endNodeName,
        String startVehicleTime,
        String endVehicleTime
) {}
