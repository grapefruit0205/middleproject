package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.domain.*;
import com.middleproject.reminder.transport.infrastructure.adapter.PublicTransportHttpAdapter;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PublicTransportHttpAdapterTest {

    private TransportHttpClient httpClient;
    private PublicTransportProperties properties;
    private AwsPublicDataCredentialProvider credentialProvider;
    private PublicTransportHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        httpClient = mock(TransportHttpClient.class);
        credentialProvider = mock(AwsPublicDataCredentialProvider.class);
        properties = new PublicTransportProperties();
        when(credentialProvider.load(any())).thenReturn(
                new PublicDataCredentials("dummy-seoul-key", "dummy-datagokr-key")
        );
        adapter = new PublicTransportHttpAdapter(httpClient, properties, credentialProvider);
    }

    private HttpResponse<String> mockResponse(int statusCode, String body) {
        return new HttpResponse<String>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public java.net.http.HttpRequest request() {
                return null;
            }

            @Override
            public java.util.Optional<HttpResponse<String>> previousResponse() {
                return java.util.Optional.empty();
            }

            @Override
            public java.net.http.HttpHeaders headers() {
                return java.net.http.HttpHeaders.of(java.util.Map.of(), (k, v) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost");
            }

            @Override
            public java.net.http.HttpClient.Version version() {
                return java.net.http.HttpClient.Version.HTTP_1_1;
            }
        };
    }

    // --- Operation 1: Seoul real-time subway arrivals ---
    @Test
    void seoulSubwayDisabledByDefaultAndMakesNoHttpCall() throws Exception {
        properties.setSeoulRealtimeEnabled(false);

        TransportOutcome<List<RealtimeSubwayArrival>> outcome = adapter.getRealtimeSubwayArrivals("강남", 5);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.DISABLED_INSECURE, outcome.failureKind());
        verifyNoInteractions(httpClient);
    }

    @Test
    void seoulSubwayParsesSuccessWhenEnabled() throws Exception {
        properties.setSeoulRealtimeEnabled(true);
        String json = """
                {
                  "errorMessage": {
                    "status": 200,
                    "code": "INFO-000",
                    "message": "정상 처리되었습니다.",
                    "total": 1
                  },
                  "realtimeArrivalList": [
                    {
                      "statnNm": "강남",
                      "trainLineNm": "성수행 - 역삼방면",
                      "bstatnNm": "성수",
                      "arvlMsg2": "2분 후 (역삼)",
                      "arvlMsg3": "역삼",
                      "barvlDt": "120",
                      "recptnDt": "2026-08-17 14:30:00"
                    }
                  ]
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<RealtimeSubwayArrival>> outcome = adapter.getRealtimeSubwayArrivals("강남", 5);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        RealtimeSubwayArrival item = outcome.value().getFirst();
        assertEquals("강남", item.stationName());
        assertEquals("성수행 - 역삼방면", item.lineName());
        assertEquals("성수", item.destinationName());
        assertEquals(120, item.arrivalSeconds());
    }

    @Test
    void seoulSubwayParsesEmptyWhenNoData() throws Exception {
        properties.setSeoulRealtimeEnabled(true);
        String json = """
                {
                  "errorMessage": {
                    "status": 200,
                    "code": "INFO-200",
                    "message": "해당하는 데이터가 없습니다."
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<RealtimeSubwayArrival>> outcome = adapter.getRealtimeSubwayArrivals("강남", 5);

        assertTrue(outcome.isEmpty());
    }

    // --- Operation 2: TAGO subway station lookup ---
    @Test
    void tagoSubwayStationLookupParsesSuccessArray() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "subwayStationId": "SUB1001", "subwayStationName": "판교", "subwayRouteName": "신분당선" },
                          { "subwayStationId": "SUB1002", "subwayStationName": "판교", "subwayRouteName": "경강선" }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 2
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(2, outcome.value().size());
        assertEquals("SUB1001", outcome.value().get(0).stationId());
        assertEquals("판교", outcome.value().get(0).stationName());
        assertEquals("신분당선", outcome.value().get(0).routeName());

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(httpClient).sendGet(uriCaptor.capture(), any());
        assertTrue(uriCaptor.getValue().toString().startsWith("https://apis.data.go.kr/1613000/SubwayInfo/GetKwrdFndSubwaySttnList"));
        assertTrue(uriCaptor.getValue().toString().contains("subwayStationName=%ED%8C%90%EA%B5%90"));
    }

    @Test
    void tagoSubwayStationLookupHandlesXmlAuthError() throws Exception {
        String xml = """
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <errMsg>SERVICE ERROR</errMsg>
                    <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                    <returnReasonCode>30</returnReasonCode>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, xml));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.AUTH_REJECTED, outcome.failureKind());
    }

    @Test
    void tagoSubwayStationLookupHandlesRateLimit() throws Exception {
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(429, "Too Many Requests"));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.RATE_LIMITED, outcome.failureKind());
    }

    @Test
    void tagoSubwayStationLookupHandlesTimeout() throws Exception {
        when(httpClient.sendGet(any(URI.class), any())).thenThrow(new HttpTimeoutException("read timed out"));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.TIMEOUT, outcome.failureKind());
    }

    // --- Operation 3: TAGO subway station schedule ---
    @Test
    void tagoSubwayScheduleParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "arrSubwayStationNm": "양재", "trainNo": "K1001", "depTime": "06:15:00", "arrTime": "06:14:00" }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<SubwayScheduleItem>> outcome = adapter.getSubwayStationSchedule("SUB1001", "01", "01", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("양재", outcome.value().getFirst().destinationStationName());
        assertEquals("06:15:00", outcome.value().getFirst().departureTime());
    }

    // --- Operation 4: TAGO coordinate-proximity bus stops ---
    @Test
    void tagoCoordinateProximityBusStopsParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "nodeid": "BS123", "nodenm": "강남역", "nodeno": "23101", "citycode": 23, "gpslati": 37.498, "gpslong": 127.027 }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<NearbyBusStop>> outcome = adapter.getNearbyBusStops(37.498, 127.027, 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("BS123", outcome.value().getFirst().nodeId());
        assertEquals("강남역", outcome.value().getFirst().nodeName());
    }

    @Test
    void tagoCoordinateProximityBusStopsRejectsOutOfBoundsCoordinates() {
        TransportOutcome<List<NearbyBusStop>> outcome = adapter.getNearbyBusStops(10.0, 50.0, 1, 10);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.MALFORMED, outcome.failureKind());
        verifyNoInteractions(httpClient);
    }

    // --- Operation 5: TAGO bus arrivals by stop ---
    @Test
    void tagoBusArrivalsParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "routeid": "R100", "routeno": "472", "routetp": "간선", "arrprevstationcnt": 2, "arrtime": 180 }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<BusArrival>> outcome = adapter.getBusArrivals(23, "BS123", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("472", outcome.value().getFirst().routeNo());
        assertEquals(180, outcome.value().getFirst().remainingSeconds());
    }

    // --- Operation 6: TAGO route-number lookup ---
    @Test
    void tagoRouteNumberLookupParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "routeid": "R100", "routeno": "472", "routetp": "간선", "startnodenm": "개포동", "endnodenm": "신촌", "startvehicletime": "0430", "endvehicletime": "2300" }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<BusRoute>> outcome = adapter.searchBusRoutes(23, "472", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("472", outcome.value().getFirst().routeNo());
        assertEquals("개포동", outcome.value().getFirst().startNodeName());
    }

    // --- Operation 7: TAGO express-bus arrival prediction ---
    @Test
    void tagoExpressBusArrivalsParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "routenm": "서울경부-부산", "busgradenm": "우등", "deptmnnm": "서울경부", "arrtmnnm": "부산", "arrtimemnt": 240, "depplandtime": "1400" }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<ExpressBusArrival>> outcome = adapter.getExpressBusArrivals("010", "700", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("서울경부-부산", outcome.value().getFirst().routeName());
        assertEquals(240, outcome.value().getFirst().remainingMinutes());
    }

    // --- Operation 8: TAGO intercity-bus scheduled services ---
    @Test
    void tagoIntercityBusScheduleParsesSuccess() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "item": [
                          { "depterminalnm": "동서울", "arrterminalnm": "속초", "depplandtime": "202608170830", "arrplandtime": "202608171040", "grade": "우등", "charge": 19700 }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<IntercityBusSchedule>> outcome = adapter.getIntercityBusSchedule("NAEK010", "NAEK020", "20260817", 1, 10);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.value().size());
        assertEquals("동서울", outcome.value().getFirst().departureTerminalName());
        assertEquals(19700, outcome.value().getFirst().chargeKrw());
    }

    @Test
    void tagoIntercityBusScheduleRejectsInvalidDateFormat() {
        TransportOutcome<List<IntercityBusSchedule>> outcome = adapter.getIntercityBusSchedule("NAEK010", "NAEK020", "2026-08-17", 1, 10);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.MALFORMED, outcome.failureKind());
        verifyNoInteractions(httpClient);
    }

    @Test
    void providerExceptionsDoNotLeakRawExceptionMessageOrSensitiveUris() throws Exception {
        properties.setSeoulRealtimeEnabled(true);
        when(httpClient.sendGet(any(URI.class), any()))
                .thenThrow(new RuntimeException("Connection failed to http://swopenapi.seoul.go.kr/api/subway/SECRET_KEY_12345/json/"));

        TransportOutcome<List<RealtimeSubwayArrival>> outcome = adapter.getRealtimeSubwayArrivals("강남", 5);

        assertFalse(outcome.isSuccess());
        assertEquals(TransportOutcome.FailureKind.MALFORMED, outcome.failureKind());
        assertNotNull(outcome.errorMessage());
        assertFalse(outcome.errorMessage().contains("SECRET_KEY_12345"), "Must not leak credentials or raw URI");
        assertFalse(outcome.errorMessage().contains("Connection failed to"), "Must not leak raw exception message");
    }

    @Test
    void arbitraryXmlContainingNumber22IsNotTreatedAsRateLimited() throws Exception {
        String xml = """
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <errMsg>UNKNOWN ERROR 22</errMsg>
                    <returnReasonCode>99</returnReasonCode>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, xml));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertFalse(outcome.isSuccess());
        assertNotEquals(TransportOutcome.FailureKind.RATE_LIMITED, outcome.failureKind(),
                "Arbitrary XML containing text '22' should not be classified as rate-limited");
        assertEquals(TransportOutcome.FailureKind.MALFORMED, outcome.failureKind());
    }

    @Test
    void malformedItemsStructureReturnsMalformedNotSuccessWithEmptyList() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": "corrupted-string-not-object",
                      "totalCount": 5
                    }
                  }
                }
                """;
        when(httpClient.sendGet(any(URI.class), any())).thenReturn(mockResponse(200, json));

        TransportOutcome<List<SubwayStation>> outcome = adapter.searchSubwayStations("판교", 1, 10);

        assertFalse(outcome.isSuccess(), "Malformed items structure must not return success");
        assertEquals(TransportOutcome.FailureKind.MALFORMED, outcome.failureKind());
    }
}
