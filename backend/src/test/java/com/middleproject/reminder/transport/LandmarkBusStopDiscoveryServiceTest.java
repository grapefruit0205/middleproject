package com.middleproject.reminder.transport;

import com.middleproject.reminder.application.LandmarkBusStopDiscoveryService;
import com.middleproject.reminder.transport.domain.BusRoute;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.NearbyBusStop;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.port.LandmarkSearchPort;
import com.middleproject.reminder.transport.port.PublicTransportPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LandmarkBusStopDiscoveryServiceTest {

    @Test
    void resolvesLandmarkAndReturnsThreeNearestStopsWithDirectionAndRoutes() {
        LandmarkSearchPort landmarks = mock(LandmarkSearchPort.class);
        PublicTransportPort transport = mock(PublicTransportPort.class);
        when(landmarks.search("강남역 11번 출구", 3)).thenReturn(TransportOutcome.success(List.of(
                new LandmarkCandidate("강남역 11번 출구", "서울 강남구 강남대로 396", 37.4981, 127.0276),
                new LandmarkCandidate("강남역 11번 출구 앞", "서울 강남구 역삼동", 37.4983, 127.0278)
        )));
        when(transport.getNearbyBusStops(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(TransportOutcome.success(List.of(
                        new NearbyBusStop("STOP-3", "강남역.역삼세무서", "23287", 11, 37.4998, 127.0290),
                        new NearbyBusStop("STOP-1", "강남역11번출구", "23285", 11, 37.4982, 127.0277),
                        new NearbyBusStop("STOP-4", "신분당선강남역", "22009", 11, 37.5010, 127.0250),
                        new NearbyBusStop("STOP-2", "강남역12번출구", "23284", 11, 37.4995, 127.0288)
                )));
        when(transport.getRoutesThroughStop(anyInt(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> TransportOutcome.success(List.of(
                        new BusRoute("ROUTE-1", "341", "간선버스", "하남공영차고지", "강남역", "04:30", "23:40")
                )));

        TransportOutcome<LandmarkBusStopDiscoveryService.DiscoveryResult> outcome =
                new LandmarkBusStopDiscoveryService(landmarks, transport)
                        .find("강남역 11번 출구", 3);

        assertEquals(3, outcome.value().candidates().size());
        assertEquals("STOP-1", outcome.value().candidates().get(0).nodeId());
        assertEquals("341", outcome.value().candidates().get(0).routes().get(0).routeNo());
        assertEquals("하남공영차고지 → 강남역", outcome.value().candidates().get(0).routes().get(0).direction());
        assertFalse(outcome.value().selectionRequired());
        verify(transport, times(3)).getRoutesThroughStop(anyInt(), anyString(), anyInt(), anyInt());
    }

    @Test
    void keepsMultiplePlaceMatchesExplicitSoTheAssistantCanAskOneShortQuestion() {
        LandmarkSearchPort landmarks = mock(LandmarkSearchPort.class);
        PublicTransportPort transport = mock(PublicTransportPort.class);
        when(landmarks.search("시청", 3)).thenReturn(TransportOutcome.success(List.of(
                new LandmarkCandidate("서울특별시청", "서울 중구 세종대로 110", 37.5663, 126.9779),
                new LandmarkCandidate("부산광역시청", "부산 연제구 중앙대로 1001", 35.1798, 129.0750)
        )));
        when(transport.getNearbyBusStops(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(TransportOutcome.success(List.of(
                        new NearbyBusStop("STOP-1", "시청앞", "01001", 11, 37.5664, 126.9780)
                )));
        when(transport.getRoutesThroughStop(anyInt(), anyString(), anyInt(), anyInt()))
                .thenReturn(TransportOutcome.success(List.of()));

        var result = new LandmarkBusStopDiscoveryService(landmarks, transport).find("시청", 3).value();

        assertEquals(2, result.places().size());
        assertEquals(true, result.selectionRequired());
    }
}
