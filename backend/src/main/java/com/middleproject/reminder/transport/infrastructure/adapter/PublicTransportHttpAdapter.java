package com.middleproject.reminder.transport.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.transport.domain.*;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import com.middleproject.reminder.transport.port.PublicTransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
@ConditionalOnProperty(name = "app.transport.enabled", havingValue = "true")
public class PublicTransportHttpAdapter implements PublicTransportPort {

    private static final Logger log = LoggerFactory.getLogger(PublicTransportHttpAdapter.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SEOUL_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TAGO_BASE_URL = "https://apis.data.go.kr/1613000";
    private static final String SEOUL_SUBWAY_BASE_URL = "http://swopenapi.seoul.go.kr/api/subway";

    private final TransportHttpClient httpClient;
    private final PublicTransportProperties properties;
    private final AwsPublicDataCredentialProvider credentialProvider;
    private final ObjectMapper mapper;

    private volatile PublicDataCredentials cachedCredentials;

    @Autowired
    public PublicTransportHttpAdapter(TransportHttpClient httpClient,
                                      PublicTransportProperties properties,
                                      @Autowired(required = false) AwsPublicDataCredentialProvider credentialProvider,
                                      ObjectMapper mapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.credentialProvider = credentialProvider;
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public PublicTransportHttpAdapter(TransportHttpClient httpClient,
                                      PublicTransportProperties properties,
                                      AwsPublicDataCredentialProvider credentialProvider) {
        this(httpClient, properties, credentialProvider, new ObjectMapper());
    }

    private String resolveDataGoKrKey() {
        PublicDataCredentials creds = getCredentials();
        return creds != null ? creds.dataGoKrServiceKey() : null;
    }

    private String resolveSeoulKey() {
        PublicDataCredentials creds = getCredentials();
        return creds != null ? creds.seoulOpenDataKey() : null;
    }

    private PublicDataCredentials getCredentials() {
        if (cachedCredentials != null) {
            return cachedCredentials;
        }
        if (credentialProvider == null) {
            return null;
        }
        try {
            cachedCredentials = credentialProvider.load(properties.getSecretsSecretId());
            return cachedCredentials;
        } catch (Exception e) {
            log.warn("Failed to load public data credentials from Secrets Manager");
            return null;
        }
    }

    // --- Operation 1: Seoul real-time subway arrivals ---
    @Override
    public TransportOutcome<List<RealtimeSubwayArrival>> getRealtimeSubwayArrivals(String stationName, int limit) {
        if (!properties.isSeoulRealtimeEnabled()) {
            return TransportOutcome.disabledInsecure("Seoul subway real-time HTTP endpoint is disabled by default");
        }

        if (stationName == null || stationName.isBlank()) {
            return TransportOutcome.malformed("stationName must not be blank");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 20));

        String key = resolveSeoulKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing Seoul Open Data API key");
        }

        try {
            String encodedStation = URLEncoder.encode(stationName.trim(), StandardCharsets.UTF_8);
            String url = String.format("%s/%s/json/realtimeStationArrival/0/%d/%s",
                    SEOUL_SUBWAY_BASE_URL, key, boundedLimit, encodedStation);
            URI uri = URI.create(url);

            HttpResponse<String> response = httpClient.sendGet(uri, properties.getRequestTimeout());
            return parseSeoulSubwayResponse(response);
        } catch (HttpTimeoutException e) {
            return TransportOutcome.timeout("Seoul subway arrival request timed out");
        } catch (Exception e) {
            return TransportOutcome.malformed("Failed to query Seoul subway arrivals");
        }
    }

    private TransportOutcome<List<RealtimeSubwayArrival>> parseSeoulSubwayResponse(HttpResponse<String> response) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return TransportOutcome.authRejected("Seoul Open Data key rejected with HTTP " + response.statusCode());
        }
        if (response.statusCode() == 429) {
            return TransportOutcome.rateLimited("Seoul Open Data rate limit exceeded");
        }
        if (response.statusCode() >= 500) {
            return TransportOutcome.timeout("Seoul Open Data server error: HTTP " + response.statusCode());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return TransportOutcome.empty();
        }

        try {
            JsonNode root = mapper.readTree(body);
            JsonNode errNode = root.get("errorMessage");
            if (errNode != null) {
                String code = errNode.path("code").asText("");
                if ("INFO-200".equals(code)) {
                    return TransportOutcome.empty();
                }
                if (code.startsWith("ERROR-337") || code.startsWith("ERROR-300")) {
                    return TransportOutcome.authRejected("Seoul Open Data authentication error: " + code);
                }
                if (!code.isEmpty() && !code.equals("INFO-000")) {
                    return TransportOutcome.malformed("Seoul Open Data error response: " + code);
                }
            }

            JsonNode listNode = root.get("realtimeArrivalList");
            if (listNode == null || !listNode.isArray() || listNode.isEmpty()) {
                return TransportOutcome.empty();
            }

            List<RealtimeSubwayArrival> results = new ArrayList<>();
            for (JsonNode item : listNode) {
                String statnNm = item.path("statnNm").asText("");
                String trainLineNm = item.path("trainLineNm").asText("");
                String bstatnNm = item.path("bstatnNm").asText("");
                String arvlMsg2 = item.path("arvlMsg2").asText("");
                String arvlMsg3 = item.path("arvlMsg3").asText("");
                int barvlDt = item.path("barvlDt").asInt(0);
                String recptnDtStr = item.path("recptnDt").asText("");

                OffsetDateTime recptnDt = null;
                if (!recptnDtStr.isBlank()) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(recptnDtStr, SEOUL_DATETIME_FMT);
                        recptnDt = ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
                    } catch (Exception ignored) {}
                }
                if (recptnDt == null) {
                    recptnDt = OffsetDateTime.now(SEOUL_ZONE);
                }

                results.add(new RealtimeSubwayArrival(
                        statnNm,
                        trainLineNm,
                        bstatnNm,
                        arvlMsg3,
                        arvlMsg2,
                        barvlDt,
                        recptnDt
                ));
            }
            return TransportOutcome.success(results);
        } catch (Exception e) {
            return TransportOutcome.malformed("Malformed Seoul Subway JSON response");
        }
    }

    // --- Operation 2: TAGO subway station lookup ---
    @Override
    public TransportOutcome<List<SubwayStation>> searchSubwayStations(String subwayStationName, int pageNo, int numOfRows) {
        if (subwayStationName == null || subwayStationName.isBlank()) {
            return TransportOutcome.malformed("subwayStationName must not be blank");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));

        String query = String.format("serviceKey=%s&_type=json&subwayStationName=%s&pageNo=%d&numOfRows=%d",
                encode(key), encode(subwayStationName.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/SubwayInfo/GetKwrdFndSubwaySttnList?" + query);

        return executeTagoCall(uri, "SubwayInfo/GetKwrdFndSubwaySttnList", this::parseSubwayStation);
    }

    private SubwayStation parseSubwayStation(JsonNode item) {
        return new SubwayStation(
                item.path("subwayStationId").asText(""),
                item.path("subwayStationName").asText(""),
                item.path("subwayRouteName").asText("")
        );
    }

    // --- Operation 3: TAGO subway station schedule ---
    @Override
    public TransportOutcome<List<SubwayScheduleItem>> getSubwayStationSchedule(String subwayStationId, String dailyTypeCode, String upDownTypeCode, int pageNo, int numOfRows) {
        if (subwayStationId == null || subwayStationId.isBlank()) {
            return TransportOutcome.malformed("subwayStationId must not be blank");
        }
        if (dailyTypeCode == null || dailyTypeCode.isBlank()) {
            return TransportOutcome.malformed("dailyTypeCode must not be blank");
        }
        if (upDownTypeCode == null || upDownTypeCode.isBlank()) {
            return TransportOutcome.malformed("upDownTypeCode must not be blank");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 100));

        String query = String.format("serviceKey=%s&_type=json&subwayStationId=%s&dailyTypeCode=%s&upDownTypeCode=%s&pageNo=%d&numOfRows=%d",
                encode(key), encode(subwayStationId.trim()), encode(dailyTypeCode.trim()), encode(upDownTypeCode.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/SubwayInfo/GetSubwaySttnAcctoSchdulList?" + query);

        return executeTagoCall(uri, "SubwayInfo/GetSubwaySttnAcctoSchdulList", item -> new SubwayScheduleItem(
                subwayStationId,
                item.path("endSubwayStationNm").asText(item.path("arrSubwayStationNm").asText("")),
                item.path("trainNo").asText(""),
                item.path("depTime").asText(""),
                item.path("arrTime").asText(""),
                dailyTypeCode,
                upDownTypeCode
        ));
    }

    // --- Operation 4: TAGO coordinate-proximity bus stops ---
    @Override
    public TransportOutcome<List<NearbyBusStop>> getNearbyBusStops(double gpsLati, double gpsLong, int pageNo, int numOfRows) {
        if (gpsLati < 33.0 || gpsLati > 39.0 || gpsLong < 124.0 || gpsLong > 132.0) {
            return TransportOutcome.malformed("WGS84 coordinates out of valid Korea boundary");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));

        String query = String.format("serviceKey=%s&_type=json&gpsLati=%f&gpsLong=%f&pageNo=%d&numOfRows=%d",
                encode(key), gpsLati, gpsLong, boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/BusSttnInfoInqireService/getCrdntPrxmtSttnList?" + query);

        return executeTagoCall(uri, "BusSttnInfoInqireService/getCrdntPrxmtSttnList", item -> new NearbyBusStop(
                item.path("nodeid").asText(""),
                item.path("nodenm").asText(""),
                item.path("nodeno").asText(""),
                item.path("citycode").asInt(0),
                item.path("gpslati").asDouble(0.0),
                item.path("gpslong").asDouble(0.0)
        ));
    }

    // --- Operation 5: TAGO bus arrivals by stop ---
    @Override
    public TransportOutcome<List<BusArrival>> getBusArrivals(int cityCode, String nodeId, int pageNo, int numOfRows) {
        if (cityCode <= 0) {
            return TransportOutcome.malformed("cityCode must be positive");
        }
        if (nodeId == null || nodeId.isBlank()) {
            return TransportOutcome.malformed("nodeId must not be blank");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));

        String query = String.format("serviceKey=%s&_type=json&cityCode=%d&nodeId=%s&pageNo=%d&numOfRows=%d",
                encode(key), cityCode, encode(nodeId.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?" + query);

        return executeTagoCall(uri, "ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList", item -> new BusArrival(
                item.path("routeid").asText(""),
                item.path("routeno").asText(""),
                item.path("routetp").asText(""),
                item.path("arrprevstationcnt").asInt(0),
                item.path("arrtime").asInt(0)
        ));
    }

    @Override
    public TransportOutcome<List<BusRoute>> getRoutesThroughStop(int cityCode, String nodeId, int pageNo, int numOfRows) {
        if (cityCode <= 0) return TransportOutcome.malformed("cityCode must be positive");
        if (nodeId == null || nodeId.isBlank()) return TransportOutcome.malformed("nodeId must not be blank");
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) return TransportOutcome.authRejected("Missing data.go.kr service key");
        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));
        String query = String.format("serviceKey=%s&_type=json&cityCode=%d&nodeid=%s&pageNo=%d&numOfRows=%d",
                encode(key), cityCode, encode(nodeId.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/BusSttnInfoInqireService/getSttnThrghRouteList?" + query);
        return executeTagoCall(uri, "BusSttnInfoInqireService/getSttnThrghRouteList", item -> new BusRoute(
                item.path("routeid").asText(""), item.path("routeno").asText(""),
                item.path("routetp").asText(""), item.path("startnodenm").asText(""),
                item.path("endnodenm").asText(""), "", ""));
    }

    // --- Operation 6: TAGO route-number lookup ---
    @Override
    public TransportOutcome<List<BusRoute>> searchBusRoutes(int cityCode, String routeNo, int pageNo, int numOfRows) {
        if (cityCode <= 0) {
            return TransportOutcome.malformed("cityCode must be positive");
        }
        if (routeNo == null || routeNo.isBlank()) {
            return TransportOutcome.malformed("routeNo must not be blank");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));

        String query = String.format("serviceKey=%s&_type=json&cityCode=%d&routeNo=%s&pageNo=%d&numOfRows=%d",
                encode(key), cityCode, encode(routeNo.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/BusRouteInfoInqireService/getRouteNoList?" + query);

        return executeTagoCall(uri, "BusRouteInfoInqireService/getRouteNoList", item -> new BusRoute(
                item.path("routeid").asText(""),
                item.path("routeno").asText(""),
                item.path("routetp").asText(""),
                item.path("startnodenm").asText(""),
                item.path("endnodenm").asText(""),
                item.path("startvehicletime").asText(""),
                item.path("endvehicletime").asText("")
        ));
    }

    // --- Operation 7: TAGO express-bus arrival prediction ---
    @Override
    public TransportOutcome<List<ExpressBusArrival>> getExpressBusArrivals(String depTerminalCode, String arrTerminalCode, int pageNo, int numOfRows) {
        if (depTerminalCode == null || depTerminalCode.isBlank()) {
            return TransportOutcome.malformed("depTerminalCode must not be blank");
        }
        if (arrTerminalCode == null || arrTerminalCode.isBlank()) {
            return TransportOutcome.malformed("arrTerminalCode must not be blank");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 50));

        String query = String.format("serviceKey=%s&_type=json&depTmnCd=%s&arrTmnCd=%s&pageNo=%d&numOfRows=%d",
                encode(key), encode(depTerminalCode.trim()), encode(arrTerminalCode.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/ExpBusArrInfo/GetExpBusArrPrdtInfo?" + query);

        return executeTagoCall(uri, "ExpBusArrInfo/GetExpBusArrPrdtInfo", item -> new ExpressBusArrival(
                item.path("routenm").asText(""),
                item.path("busgradenm").asText(""),
                item.path("deptmnnm").asText(""),
                item.path("arrtmnnm").asText(""),
                item.path("arrtimemnt").asInt(0),
                item.path("depplandtime").asText("")
        ));
    }

    // --- Operation 8: TAGO intercity-bus scheduled services ---
    @Override
    public TransportOutcome<List<IntercityBusSchedule>> getIntercityBusSchedule(String depTerminalId, String arrTerminalId, String depPlandTime, int pageNo, int numOfRows) {
        if (depTerminalId == null || depTerminalId.isBlank()) {
            return TransportOutcome.malformed("depTerminalId must not be blank");
        }
        if (arrTerminalId == null || arrTerminalId.isBlank()) {
            return TransportOutcome.malformed("arrTerminalId must not be blank");
        }
        if (depPlandTime == null || !depPlandTime.matches("\\d{8}")) {
            return TransportOutcome.malformed("depPlandTime must match yyyyMMdd format");
        }
        String key = resolveDataGoKrKey();
        if (key == null || key.isBlank()) {
            return TransportOutcome.authRejected("Missing data.go.kr service key");
        }

        int boundedPage = Math.max(1, pageNo);
        int boundedRows = Math.max(1, Math.min(numOfRows, 100));

        String query = String.format("serviceKey=%s&_type=json&depTerminalId=%s&arrTerminalId=%s&depPlandTime=%s&pageNo=%d&numOfRows=%d",
                encode(key), encode(depTerminalId.trim()), encode(arrTerminalId.trim()), encode(depPlandTime.trim()), boundedPage, boundedRows);
        URI uri = URI.create(TAGO_BASE_URL + "/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo?" + query);

        return executeTagoCall(uri, "SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo", item -> new IntercityBusSchedule(
                item.path("depterminalnm").asText(""),
                item.path("arrterminalnm").asText(""),
                item.path("depplandtime").asText(""),
                item.path("arrplandtime").asText(""),
                item.path("grade").asText(""),
                item.path("charge").asInt(0)
        ));
    }

    // --- Common TAGO execution and parsing ---
    private <T> TransportOutcome<List<T>> executeTagoCall(URI uri, String endpointLabel, Function<JsonNode, T> itemMapper) {
        try {
            HttpResponse<String> response = httpClient.sendGet(uri, properties.getRequestTimeout());
            return parseTagoResponse(response, endpointLabel, itemMapper);
        } catch (HttpTimeoutException e) {
            return TransportOutcome.timeout(endpointLabel + " request timed out");
        } catch (Exception e) {
            return TransportOutcome.malformed(endpointLabel + " request failed");
        }
    }

    private <T> TransportOutcome<List<T>> parseTagoResponse(HttpResponse<String> response, String endpointLabel, Function<JsonNode, T> itemMapper) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return TransportOutcome.authRejected("TAGO service key rejected: HTTP " + status);
        }
        if (status == 429) {
            return TransportOutcome.rateLimited("TAGO rate limit exceeded: HTTP 429");
        }
        if (status >= 500) {
            return TransportOutcome.timeout("TAGO server error: HTTP " + status);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return TransportOutcome.empty();
        }

        // Detect XML error responses from public data portal gateway
        if (body.stripLeading().startsWith("<")) {
            if (body.contains("SERVICE_KEY_IS_NOT_REGISTERED")
                    || body.contains("returnAuthMsg")
                    || body.contains("SERVICE KEY")
                    || body.contains("<returnReasonCode>30</returnReasonCode>")
                    || body.contains("<returnReasonCode>31</returnReasonCode>")
                    || body.contains("<returnReasonCode>32</returnReasonCode>")) {
                return TransportOutcome.authRejected("TAGO gateway authentication error");
            }
            if (body.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS")
                    || body.contains("<returnReasonCode>22</returnReasonCode>")) {
                return TransportOutcome.rateLimited("TAGO daily request quota exceeded");
            }
            return TransportOutcome.malformed("TAGO returned unexpected XML response for " + endpointLabel);
        }

        try {
            JsonNode root = mapper.readTree(body);
            JsonNode responseNode = root.has("response") ? root.get("response") : root;
            JsonNode header = responseNode.get("header");

            if (header != null) {
                String resultCode = header.path("resultCode").asText("");
                String resultMsg = header.path("resultMsg").asText("");

                if ("03".equals(resultCode) || "NODATA_ERROR".equalsIgnoreCase(resultMsg)) {
                    return TransportOutcome.empty();
                }
                if ("22".equals(resultCode) || resultMsg.contains("LIMITED_NUMBER")) {
                    return TransportOutcome.rateLimited("TAGO rate limit: " + resultCode);
                }
                if ("30".equals(resultCode) || "31".equals(resultCode) || "32".equals(resultCode) || resultMsg.contains("SERVICE_KEY")) {
                    return TransportOutcome.authRejected("TAGO authentication rejected: " + resultCode);
                }
                if (!"00".equals(resultCode) && !"0".equals(resultCode) && !resultCode.isEmpty()) {
                    return TransportOutcome.malformed("TAGO error code: " + resultCode + " (" + resultMsg + ")");
                }
            }

            JsonNode bodyNode = responseNode.get("body");
            if (bodyNode == null || bodyNode.isNull()) {
                return TransportOutcome.empty();
            }

            int totalCount = bodyNode.path("totalCount").asInt(0);
            JsonNode itemsNode = bodyNode.get("items");
            if (itemsNode == null || itemsNode.isNull()) {
                return totalCount == 0 ? TransportOutcome.empty() : TransportOutcome.malformed("Missing items node for " + endpointLabel);
            }
            if (itemsNode.isTextual()) {
                if (itemsNode.asText().isBlank() && totalCount == 0) {
                    return TransportOutcome.empty();
                }
                return TransportOutcome.malformed("Malformed items structure for " + endpointLabel);
            }
            if (!itemsNode.isObject()) {
                return TransportOutcome.malformed("Malformed items structure for " + endpointLabel);
            }

            JsonNode itemNode = itemsNode.get("item");
            if (itemNode == null || itemNode.isNull()) {
                return totalCount == 0 ? TransportOutcome.empty() : TransportOutcome.malformed("Missing item node for " + endpointLabel);
            }
            if (itemNode.isTextual()) {
                if (itemNode.asText().isBlank() && totalCount == 0) {
                    return TransportOutcome.empty();
                }
                return TransportOutcome.malformed("Malformed item node for " + endpointLabel);
            }
            if (itemNode.isArray() && itemNode.isEmpty()) {
                return TransportOutcome.empty();
            }

            List<T> results = new ArrayList<>();
            if (itemNode.isArray()) {
                for (JsonNode elem : itemNode) {
                    if (elem.isObject()) {
                        T mapped = itemMapper.apply(elem);
                        if (mapped != null) {
                            results.add(mapped);
                        }
                    } else {
                        return TransportOutcome.malformed("Malformed item element for " + endpointLabel);
                    }
                }
            } else if (itemNode.isObject()) {
                T mapped = itemMapper.apply(itemNode);
                if (mapped != null) {
                    results.add(mapped);
                }
            } else {
                return TransportOutcome.malformed("Malformed item structure for " + endpointLabel);
            }

            return results.isEmpty() ? TransportOutcome.empty() : TransportOutcome.success(results);
        } catch (Exception e) {
            return TransportOutcome.malformed("Malformed TAGO JSON for " + endpointLabel);
        }
    }

    private static String encode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
