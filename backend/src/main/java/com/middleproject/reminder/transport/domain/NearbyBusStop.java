package com.middleproject.reminder.transport.domain;

public record NearbyBusStop(
        String nodeId,
        String nodeName,
        String nodeNo,
        Integer cityCode,
        Double latitude,
        Double longitude
) {
    @Override
    public String toString() {
        return "NearbyBusStop[nodeId=" + nodeId + ", nodeName=" + nodeName + ", nodeNo=" + nodeNo + ", cityCode=" + cityCode + "]";
    }
}
