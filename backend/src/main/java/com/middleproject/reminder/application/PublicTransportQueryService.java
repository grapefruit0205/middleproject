package com.middleproject.reminder.application;

import com.middleproject.reminder.transport.domain.BusArrival;
import com.middleproject.reminder.transport.domain.BusRoute;
import com.middleproject.reminder.transport.domain.ExpressBusArrival;
import com.middleproject.reminder.transport.domain.IntercityBusSchedule;
import com.middleproject.reminder.transport.domain.NearbyBusStop;
import com.middleproject.reminder.transport.domain.RealtimeSubwayArrival;
import com.middleproject.reminder.transport.domain.SubwayScheduleItem;
import com.middleproject.reminder.transport.domain.SubwayStation;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.PublicTransportPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublicTransportQueryService {

    private final PublicTransportPort transportPort;

    @Autowired
    public PublicTransportQueryService(@Autowired(required = false) PublicTransportPort transportPort) {
        this.transportPort = transportPort != null ? transportPort : createDisabledPort();
    }

    public TransportOutcome<List<RealtimeSubwayArrival>> getRealtimeSubwayArrivals(String stationName, int limit) {
        return transportPort.getRealtimeSubwayArrivals(stationName, limit);
    }

    public TransportOutcome<List<SubwayStation>> searchSubwayStations(String subwayStationName, int pageNo, int numOfRows) {
        return transportPort.searchSubwayStations(subwayStationName, pageNo, numOfRows);
    }

    public TransportOutcome<List<SubwayScheduleItem>> getSubwayStationSchedule(String subwayStationId, String dailyTypeCode, String upDownTypeCode, int pageNo, int numOfRows) {
        return transportPort.getSubwayStationSchedule(subwayStationId, dailyTypeCode, upDownTypeCode, pageNo, numOfRows);
    }

    public TransportOutcome<List<NearbyBusStop>> getNearbyBusStops(double gpsLati, double gpsLong, int pageNo, int numOfRows) {
        return transportPort.getNearbyBusStops(gpsLati, gpsLong, pageNo, numOfRows);
    }

    public TransportOutcome<List<BusArrival>> getBusArrivals(int cityCode, String nodeId, int pageNo, int numOfRows) {
        return transportPort.getBusArrivals(cityCode, nodeId, pageNo, numOfRows);
    }

    public TransportOutcome<List<BusRoute>> searchBusRoutes(int cityCode, String routeNo, int pageNo, int numOfRows) {
        return transportPort.searchBusRoutes(cityCode, routeNo, pageNo, numOfRows);
    }

    public TransportOutcome<List<ExpressBusArrival>> getExpressBusArrivals(String depTerminalCode, String arrTerminalCode, int pageNo, int numOfRows) {
        return transportPort.getExpressBusArrivals(depTerminalCode, arrTerminalCode, pageNo, numOfRows);
    }

    public TransportOutcome<List<IntercityBusSchedule>> getIntercityBusSchedule(String depTerminalId, String arrTerminalId, String depPlandTime, int pageNo, int numOfRows) {
        return transportPort.getIntercityBusSchedule(depTerminalId, arrTerminalId, depPlandTime, pageNo, numOfRows);
    }

    private static PublicTransportPort createDisabledPort() {
        return new PublicTransportPort() {
            @Override
            public TransportOutcome<List<RealtimeSubwayArrival>> getRealtimeSubwayArrivals(String stationName, int limit) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<SubwayStation>> searchSubwayStations(String subwayStationName, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<SubwayScheduleItem>> getSubwayStationSchedule(String subwayStationId, String dailyTypeCode, String upDownTypeCode, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<NearbyBusStop>> getNearbyBusStops(double gpsLati, double gpsLong, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<BusRoute>> getRoutesThroughStop(int cityCode, String nodeId, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<BusArrival>> getBusArrivals(int cityCode, String nodeId, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<BusRoute>> searchBusRoutes(int cityCode, String routeNo, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<ExpressBusArrival>> getExpressBusArrivals(String depTerminalCode, String arrTerminalCode, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }

            @Override
            public TransportOutcome<List<IntercityBusSchedule>> getIntercityBusSchedule(String depTerminalId, String arrTerminalId, String depPlandTime, int pageNo, int numOfRows) {
                return TransportOutcome.disabledInsecure("Public transport port is disabled");
            }
        };
    }
}
