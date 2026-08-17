package com.middleproject.reminder.transport.port;

import com.middleproject.reminder.transport.domain.*;

import java.util.List;

public interface PublicTransportPort {

    /**
     * Operation 1: Seoul real-time subway arrivals (swopenapi.seoul.go.kr)
     * Security gate: Must be disabled by default and make NO live HTTP call unless enabled.
     */
    TransportOutcome<List<RealtimeSubwayArrival>> getRealtimeSubwayArrivals(String stationName, int limit);

    /**
     * Operation 2: TAGO subway station lookup (GetKwrdFndSubwaySttnList)
     */
    TransportOutcome<List<SubwayStation>> searchSubwayStations(String subwayStationName, int pageNo, int numOfRows);

    /**
     * Operation 3: TAGO subway station schedule (GetSubwaySttnAcctoSchdulList)
     */
    TransportOutcome<List<SubwayScheduleItem>> getSubwayStationSchedule(String subwayStationId, String dailyTypeCode, String upDownTypeCode, int pageNo, int numOfRows);

    /**
     * Operation 4: TAGO coordinate-proximity bus stops (getCrdntPrxmtSttnList)
     * Coordinates are never logged or persisted.
     */
    TransportOutcome<List<NearbyBusStop>> getNearbyBusStops(double gpsLati, double gpsLong, int pageNo, int numOfRows);

    /** Supporting operation: TAGO routes that pass through one discovered stop. */
    TransportOutcome<List<BusRoute>> getRoutesThroughStop(int cityCode, String nodeId, int pageNo, int numOfRows);

    /**
     * Operation 5: TAGO bus arrivals by stop (getSttnAcctoArvlPrearngeInfoList)
     */
    TransportOutcome<List<BusArrival>> getBusArrivals(int cityCode, String nodeId, int pageNo, int numOfRows);

    /**
     * Operation 6: TAGO route-number lookup (getRouteNoList)
     */
    TransportOutcome<List<BusRoute>> searchBusRoutes(int cityCode, String routeNo, int pageNo, int numOfRows);

    /**
     * Operation 7: TAGO express-bus arrival prediction (GetExpBusArrPrdtInfo)
     */
    TransportOutcome<List<ExpressBusArrival>> getExpressBusArrivals(String depTerminalCode, String arrTerminalCode, int pageNo, int numOfRows);

    /**
     * Operation 8: TAGO intercity-bus scheduled services (GetStrtpntAlocFndSuberbsBusInfo)
     */
    TransportOutcome<List<IntercityBusSchedule>> getIntercityBusSchedule(String depTerminalId, String arrTerminalId, String depPlandTime, int pageNo, int numOfRows);
}
