package com.middleproject.reminder.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.middleproject.reminder.application.McpAuditService;
import com.middleproject.reminder.application.McpReminderQueryService;
import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.PublicTransportQueryService;
import com.middleproject.reminder.application.LandmarkBusStopDiscoveryService;
import com.middleproject.reminder.application.PlaceDiscoveryService;
import com.middleproject.reminder.application.PublicTransitRoutePreviewService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.TravelRecommendationService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.application.TransitFavoriteService;
import com.middleproject.reminder.application.OriginFavoriteService;
import com.middleproject.reminder.device.DevicePairingService;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

@RestController
@RequestMapping("/api/mcp")
public class McpAdapterController {
    private static final DateTimeFormatter STRICT_DATE_FMT = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final List<String> TOOL_NAMES = List.of("create_reminder", "list_reminders", "get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status",
            "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
            "next_private_car_question", "preview_private_car_route", "confirm_private_car_route",
            "get_trip_travel_context", "record_trip_followup_consent", "get_trip_recommendations",
            "create_device_pairing_code", "search_subway_stations", "get_realtime_subway_arrivals",
            "get_subway_station_schedule", "find_nearby_bus_stops", "find_bus_stops_by_landmark", "get_bus_arrivals", "search_bus_routes",
            "get_express_bus_arrivals", "get_intercity_bus_schedule",
            "save_subway_favorite", "save_bus_favorite", "list_transit_favorites",
            "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route",
            "save_origin_favorite", "list_origin_favorites", "delete_origin_favorite");
    private static final List<String> DEPARTURE_TIMINGS = List.of("SAME_DAY", "PREVIOUS_DAY");
    private static final List<String> RECOMMENDATION_SORTS = List.of("DISTANCE", "PRICE", "RATING");
    private static final List<String> DAILY_TYPE_CODES = List.of("01", "02", "03");
    private static final List<String> UP_DOWN_TYPE_CODES = List.of("U", "D");
    private static final Map<String, List<String>> TOOL_ARGS = Map.ofEntries(
            Map.entry("create_reminder", List.of("eventId", "policyId", "idempotencyKey")),
            Map.entry("list_reminders", List.of()),
            Map.entry("get_reminder", List.of("reminderId")),
            Map.entry("update_reminder", List.of("reminderId", "eventId", "policyId", "expectedVersion", "idempotencyKey")),
            Map.entry("cancel_reminder", List.of("reminderId", "expectedVersion", "idempotencyKey")),
            Map.entry("get_delivery_status", List.of("reminderId")),
            Map.entry("create_trip_draft", List.of("departure", "destination", "departureAt", "returnAt", "idempotencyKey")),
            Map.entry("answer_trip_question", List.of("tripId", "question", "answer", "idempotencyKey")),
            Map.entry("confirm_trip", List.of("tripId", "confirmation", "idempotencyKey")),
            Map.entry("cancel_trip", List.of("tripId", "expectedVersion", "idempotencyKey")),
            Map.entry("next_private_car_question", List.of("tripId")),
            Map.entry("preview_private_car_route", List.of("tripId")),
            Map.entry("confirm_private_car_route", List.of("tripId", "proposalId", "previewFetchedAt", "reminderLeadMinutes", "confirmationId", "idempotencyKey")),
            Map.entry("get_trip_travel_context", List.of("tripId", "departureTiming", "sort")),
            Map.entry("record_trip_followup_consent", List.of("tripId", "accepted", "idempotencyKey")),
            Map.entry("get_trip_recommendations", List.of("tripId", "sort")),
            Map.entry("create_device_pairing_code", List.of()),
            Map.entry("search_subway_stations", List.of("subwayStationName", "pageNo", "numOfRows")),
            Map.entry("get_realtime_subway_arrivals", List.of("stationName", "limit")),
            Map.entry("get_subway_station_schedule", List.of("subwayStationId", "dailyTypeCode", "upDownTypeCode", "pageNo", "numOfRows")),
            Map.entry("find_nearby_bus_stops", List.of("gpsLati", "gpsLong", "pageNo", "numOfRows")),
            Map.entry("find_bus_stops_by_landmark", List.of("landmark")),
            Map.entry("get_bus_arrivals", List.of("cityCode", "nodeId", "pageNo", "numOfRows")),
            Map.entry("search_bus_routes", List.of("cityCode", "routeNo", "pageNo", "numOfRows")),
            Map.entry("get_express_bus_arrivals", List.of("depTerminalCode", "arrTerminalCode", "pageNo", "numOfRows")),
            Map.entry("get_intercity_bus_schedule", List.of("depTerminalId", "arrTerminalId", "depPlandTime", "pageNo", "numOfRows")),
            Map.entry("save_subway_favorite", List.of("alias", "stationName", "idempotencyKey")),
            Map.entry("save_bus_favorite", List.of("alias", "cityCode", "nodeId", "stopName", "routeNo", "idempotencyKey")),
            Map.entry("list_transit_favorites", List.of()),
            Map.entry("resolve_place", List.of("query", "limit")),
            Map.entry("find_nearby_subway_stations", List.of("latitude", "longitude", "radiusMeters")),
            Map.entry("preview_public_transit_route", List.of("origin", "destination")),
            Map.entry("save_origin_favorite", List.of("alias", "placeName", "address", "latitude", "longitude", "idempotencyKey")),
            Map.entry("list_origin_favorites", List.of()),
            Map.entry("delete_origin_favorite", List.of("originFavoriteId", "idempotencyKey")));

    private static final Map<String, String> TOOL_TITLES = Map.ofEntries(
            Map.entry("create_reminder", "Create reminder for an existing event and policy"),
            Map.entry("list_reminders", "List reminders owned by the demo owner"),
            Map.entry("get_reminder", "Get one reminder by id"),
            Map.entry("update_reminder", "Update an existing reminder's event and policy"),
            Map.entry("cancel_reminder", "Cancel an existing reminder"),
            Map.entry("get_delivery_status", "Get delivery status of a reminder"),
            Map.entry("create_trip_draft", "Create a business trip draft"),
            Map.entry("answer_trip_question", "Answer the next planning question for a trip draft"),
            Map.entry("confirm_trip", "Confirm a trip draft"),
            Map.entry("cancel_trip", "Cancel a trip"),
            Map.entry("next_private_car_question", "Get the next missing private-car planning question"),
            Map.entry("preview_private_car_route", "Preview a private-car route without persisting it"),
            Map.entry("confirm_private_car_route", "Confirm a previously previewed private-car route"),
            Map.entry("get_trip_travel_context", "Get weather, packing, and accommodation context for a trip"),
            Map.entry("record_trip_followup_consent", "Record follow-up recommendation consent for a trip"),
            Map.entry("get_trip_recommendations", "Get sorted follow-up recommendations for a trip"),
            Map.entry("create_device_pairing_code", "Create a one-time device pairing code"),
            Map.entry("search_subway_stations", "Search subway stations by name"),
            Map.entry("get_realtime_subway_arrivals", "Get real-time subway arrivals"),
            Map.entry("get_subway_station_schedule", "Get subway station schedule"),
            Map.entry("find_nearby_bus_stops", "Find nearby bus stops"),
            Map.entry("find_bus_stops_by_landmark", "Find bus stops by a landmark or building"),
            Map.entry("get_bus_arrivals", "Get bus arrivals"),
            Map.entry("search_bus_routes", "Search bus routes"),
            Map.entry("get_express_bus_arrivals", "Get express bus arrivals"),
            Map.entry("get_intercity_bus_schedule", "Get intercity bus schedule"),
            Map.entry("save_subway_favorite", "Save a subway favorite for widgets"),
            Map.entry("save_bus_favorite", "Save a bus-stop favorite for widgets"),
            Map.entry("list_transit_favorites", "List saved transit favorites"),
            Map.entry("resolve_place", "Resolve a Korean place or address to coordinates"),
            Map.entry("find_nearby_subway_stations", "Find subway stations near explicit coordinates"),
            Map.entry("preview_public_transit_route", "Preview a Kakao Map public-transit route"),
            Map.entry("save_origin_favorite", "Save a trip departure location"),
            Map.entry("list_origin_favorites", "List saved trip departure locations"),
            Map.entry("delete_origin_favorite", "Delete a saved trip departure location"));

    /** MCP-standard top-level tool descriptions (model-readable, distinct from annotations.title). */
    private static final Map<String, String> TOOL_DESCRIPTIONS = Map.ofEntries(
            Map.entry("create_reminder", "Creates one reminder for an existing event and notification policy in the demo owner's scope. Replaying the same idempotencyKey with the same payload returns the stored result without creating a duplicate reminder."),
            Map.entry("list_reminders", "Lists all reminders owned by the deployment-fixed demo owner. Read-only."),
            Map.entry("get_reminder", "Returns one reminder by id when it is owned by the demo owner; other reminders are denied without leaking their existence. Read-only."),
            Map.entry("update_reminder", "Updates the event and policy of an existing demo-owner reminder with an optimistic expectedVersion and an idempotencyKey; replaying the same key returns the stored update."),
            Map.entry("cancel_reminder", "Cancels an existing demo-owner reminder with an optimistic expectedVersion and an idempotencyKey. Cancellation is destructive and must be explicitly confirmed by the user."),
            Map.entry("get_delivery_status", "Returns delivery attempts and receipts for one demo-owner reminder. Read-only."),
            Map.entry("create_trip_draft", "Creates a business trip draft with departure, destination, and departure date/time; returnAt is optional for a draft. Do not call this tool until the user has provided a departure location. If the user gives only a destination and an arrival deadline, ask where they will depart from, then gather transport and propose a feasible departure time before seeking write confirmation. Protected by an idempotencyKey."),
            Map.entry("answer_trip_question", "Records the answer to the next planning question for a trip draft. Protected by an idempotencyKey."),
            Map.entry("confirm_trip", "Confirms a trip draft so its reminder is scheduled; replaying the same idempotencyKey returns the stored confirmation."),
            Map.entry("cancel_trip", "Cancels a trip with an optimistic expectedVersion and an idempotencyKey. Cancellation is destructive and must be explicitly confirmed by the user."),
            Map.entry("next_private_car_question", "Returns the next missing private-car planning question for an existing trip, or none when the private-car inputs are complete. Read-only."),
            Map.entry("preview_private_car_route", "Previews a private-car route for an existing trip without persisting anything. Read-only."),
            Map.entry("confirm_private_car_route", "Confirms a previously previewed private-car route, scheduling its reminder. Protected by an idempotencyKey and the preview proposalId."),
            Map.entry("get_trip_travel_context", "Gets weather, packing, and accommodation context for a trip from travel providers, inserting a PROPOSED follow-up consent row when the trip has none. Repeat-safe: an existing ACCEPTED/DECLINED consent is never overwritten."),
            Map.entry("record_trip_followup_consent", "Records the user's ACCEPTED or DECLINED decision on follow-up recommendations for a trip. Protected by an idempotencyKey."),
            Map.entry("get_trip_recommendations", "Returns follow-up recommendations for a trip, gated on ACCEPTED consent and sorted by DISTANCE, PRICE, or RATING. Read-only."),
            Map.entry("create_device_pairing_code", "Issues one 5-minute one-time pairing code for the fixed demo owner so an Android companion can exchange it for a device token. The code is returned only once and only its salted hash is stored, so a second call while the code is active fails safely with a conflict. It is a mutating, non-destructive, non-idempotent operation: at most one active code exists at any time."),
            Map.entry("search_subway_stations", "Searches subway stations by name with pagination. Read-only."),
            Map.entry("get_realtime_subway_arrivals", "Gets real-time subway arrival predictions for a station name. If the user asks a vague question such as subway when does it arrive, ask for their departure station before calling this tool. Read-only."),
            Map.entry("get_subway_station_schedule", "Gets scheduled train timetables for a subway station, day type, and direction. Read-only."),
            Map.entry("find_nearby_bus_stops", "Finds bus stops near approved foreground or manually supplied WGS84 geographic coordinates with pagination. If the user asks a vague question such as bus when does it arrive, ask where they are departing from, then obtain approved foreground coordinates or a manually supplied station, stop, or landmark. Use its returned cityCode and nodeId as inputs to get_bus_arrivals. A successful empty result means no stop was found in the provider's small proximity area, so ask for a nearby landmark or stop and retry instead of treating it as an API failure. Read-only."),
            Map.entry("find_bus_stops_by_landmark", "Resolves a Korean building, station exit, or landmark such as 강남역 11번 출구 into the nearest three bus stops with distance, directions, and routes. Use this after asking one short departure-place question; never ask the user for cityCode or nodeId. When selectionRequired is false, select the first candidate and immediately call get_bus_arrivals. When true, ask only one numbered choice. Read-only."),
            Map.entry("get_bus_arrivals", "Gets predicted bus arrival times and remaining stops using cityCode and nodeId returned by find_nearby_bus_stops or another discovery tool. Do not ask the user to supply these internal identifiers unless discovery failed or multiple stops require their choice. Read-only."),
            Map.entry("search_bus_routes", "Searches bus routes by route number within a city. Read-only."),
            Map.entry("get_express_bus_arrivals", "Gets real-time express bus arrival predictions between terminal codes. Read-only."),
            Map.entry("get_intercity_bus_schedule", "Gets scheduled intercity bus timetables between terminals for a planned date. Read-only."),
            Map.entry("save_subway_favorite", "Saves or replaces an owner-scoped subway favorite in PostgreSQL for ChatGPT and the Android home widget. Ask for confirmation before writing and use an idempotencyKey."),
            Map.entry("save_bus_favorite", "Saves or replaces an owner-scoped bus stop and optional route in PostgreSQL after discovery. Never ask the user for internal identifiers; use values returned by discovery, ask for confirmation, and use an idempotencyKey."),
            Map.entry("list_transit_favorites", "Lists owner-scoped subway and bus favorites used by ChatGPT and the Android home widget. Read-only."),
            Map.entry("resolve_place", "Resolves a Korean place, building, station exit, or address into up to 15 coordinate candidates using Kakao Local. Use this before a trip route when a place name may be ambiguous; ask the user to choose only if the returned candidates are materially different. Read-only."),
            Map.entry("find_nearby_subway_stations", "Finds up to five nearby subway stations from explicit foreground or manually supplied WGS84 coordinates. Never imply background location tracking. Read-only."),
            Map.entry("preview_public_transit_route", "Resolves an origin and destination and returns a read-only Kakao Map public-transit duration, transfers, fare when provided, and an official Kakao Map handoff URL. It does not buy, reserve, or guarantee a route. Resolve ambiguous places with resolve_place before calling. Read-only."),
            Map.entry("save_origin_favorite", "Saves or replaces a confirmed owner-scoped trip departure place such as 집 or 회사 in PostgreSQL. Store only an explicitly chosen place, never background location. Ask for confirmation before writing and use an idempotencyKey."),
            Map.entry("list_origin_favorites", "Lists explicitly saved owner-scoped trip departure places. Read-only."),
            Map.entry("delete_origin_favorite", "Deletes one owner-scoped saved trip departure place. This is destructive; ask for explicit confirmation and use an idempotencyKey."));

    /** True when the tool only reads state and never mutates anything. */
    private static final Set<String> READ_ONLY_TOOLS = Set.of("list_reminders", "get_reminder", "get_delivery_status",
            "next_private_car_question", "preview_private_car_route", "get_trip_recommendations", "search_subway_stations",
            "get_realtime_subway_arrivals", "get_subway_station_schedule", "find_nearby_bus_stops", "find_bus_stops_by_landmark",
            "get_bus_arrivals", "search_bus_routes", "get_express_bus_arrivals", "get_intercity_bus_schedule",
            "list_transit_favorites", "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route",
            "list_origin_favorites");
    /** Destructive writes: cancelling state that cannot be restored by the same tool. */
    private static final Set<String> DESTRUCTIVE_TOOLS = Set.of("cancel_reminder", "cancel_trip", "delete_origin_favorite");
    /**
     * Repeat-safe tools: replaying the same arguments (including the idempotency key) is safe.
     * Every write that is protected by an idempotencyKey is replayable by contract;
     * get_trip_travel_context may insert a PROPOSED consent row but is repeat-safe because
     * an existing ACCEPTED/DECLINED status is never overwritten.
     */
    private static final Set<String> IDEMPOTENT_TOOLS = Set.of("create_reminder", "update_reminder", "cancel_reminder",
            "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
            "confirm_private_car_route", "get_trip_travel_context", "record_trip_followup_consent",
            "save_subway_favorite", "save_bus_favorite", "save_origin_favorite", "delete_origin_favorite");
    /** Open-world tools: their results depend on external travel providers. */
    private static final Set<String> OPEN_WORLD_TOOLS = Set.of("get_trip_travel_context", "get_trip_recommendations",
            "preview_private_car_route", "confirm_private_car_route", "search_subway_stations",
            "get_realtime_subway_arrivals", "get_subway_station_schedule", "find_nearby_bus_stops", "find_bus_stops_by_landmark",
            "get_bus_arrivals", "search_bus_routes", "get_express_bus_arrivals", "get_intercity_bus_schedule",
            "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route");

    private final ReminderService reminders;
    private final McpReminderQueryService queries;
    private final McpAuditService audit;
    private final TripService trips;
    private final PrivateCarPlanningService privateCar;
    private final TravelRecommendationService travel;
    private final DevicePairingService devicePairing;
    private final PublicTransportQueryService publicTransport;
    private final LandmarkBusStopDiscoveryService landmarkBusStops;
    private final TransitFavoriteService transitFavorites;
    private final PlaceDiscoveryService places;
    private final PublicTransitRoutePreviewService transitRoutePreview;
    private final OriginFavoriteService originFavorites;
    private final DemoOwnerContext demoOwner;
    private static final Logger log = LoggerFactory.getLogger(McpAdapterController.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";
    static final String PROPOSAL_ID_PATTERN = "[0-9a-f]{64}";
    private final ObjectMapper mapper;

    public McpAdapterController(ReminderService reminders, McpReminderQueryService queries, McpAuditService audit, TripService trips, PrivateCarPlanningService privateCar, TravelRecommendationService travel, DemoOwnerContext demoOwner, ObjectMapper mapper, DevicePairingService devicePairing, PublicTransportQueryService publicTransport, LandmarkBusStopDiscoveryService landmarkBusStops, TransitFavoriteService transitFavorites, PlaceDiscoveryService places, PublicTransitRoutePreviewService transitRoutePreview, OriginFavoriteService originFavorites) {
        this.reminders = reminders; this.queries = queries; this.audit = audit; this.trips = trips; this.privateCar = privateCar; this.travel = travel; this.demoOwner = demoOwner; this.mapper = mapper; this.devicePairing = devicePairing; this.publicTransport = publicTransport; this.landmarkBusStops = landmarkBusStops; this.transitFavorites = transitFavorites; this.places = places; this.transitRoutePreview = transitRoutePreview; this.originFavorites = originFavorites;
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(@RequestBody(required = false) String body) {
        JsonNode request = null, id = null; String method = null, toolName = null; UUID reminderId = null; String outcome = "FAILED";
        try {
            if (body == null || body.isBlank()) throw rpc(-32700, "Parse error");
            try (JsonParser parser = mapper.getFactory().createParser(body)) {
                request = mapper.readTree(parser);
                if (parser.nextToken() != null) throw rpc(-32700, "Parse error");
            } catch (McpException e) { throw e; } catch (Exception e) { throw rpc(-32700, "Parse error"); }
            if (request == null || !request.isObject()) throw rpc(-32600, "Invalid request");
            JsonNode version = request.get("jsonrpc"), methodNode = request.get("method");
            if (version == null || !version.isTextual() || !"2.0".equals(version.textValue()) || methodNode == null || !methodNode.isTextual() || methodNode.textValue().isBlank())
                throw rpc(-32600, "Invalid JSON-RPC envelope");
            method = methodNode.textValue();
            JsonNode params = request.get("params");
            if ("notifications/initialized".equals(method)) {
                if (request.has("id") || (params != null && !params.isObject()))
                    throw rpc(-32600, "Invalid initialized notification");
                outcome = "SUCCEEDED";
                return ResponseEntity.noContent().build();
            }
            id = request.get("id");
            if (id == null || id.isNull() || !(id.isTextual() || id.isIntegralNumber())) throw rpc(-32600, "Invalid request id");
            if (("initialize".equals(method) || "tools/list".equals(method)) && params != null && !params.isObject())
                throw rpc(-32602, "Invalid method parameters");
            if ("initialize".equals(method)) { outcome = "SUCCEEDED"; return response(id, initialize(params)); }
            if ("tools/list".equals(method)) { outcome = "SUCCEEDED"; return response(id, tools()); }
            if (!"tools/call".equals(method)) throw rpc(-32601, "Method not found");
            if (params == null || !params.isObject() || params.get("name") == null || !params.get("name").isTextual() || params.get("arguments") == null)
                throw rpc(-32602, "Invalid tool parameters");
            String tool = params.get("name").textValue();
            toolName = tool;
            JsonNode args = params.get("arguments");
            validate(tool, args);
            if (args.get("reminderId") != null) reminderId = UUID.fromString(args.get("reminderId").textValue());
            Object result = call(tool, args);
            if (result instanceof Reminder r) reminderId = r.id();
            outcome = "SUCCEEDED";
            return response(id, toolResult(result));
        } catch (McpException e) {
            return error(id, e.code, e.getMessage());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return error(id, -32003, "Reminder access denied");
            log.warn("MCP business failure for method {} with status {}", method, e.getStatusCode());
            return error(id, -32002, "Request could not be completed");
        } catch (Exception e) {
            log.error("Unexpected MCP failure for method {}", method, e);
            return error(id, -32000, "Tool execution failed");
        } finally {
            try {
                audit.record(toolName != null ? toolName : method, id == null ? null : id.asText(), outcome, reminderId);
            } catch (Exception e) {
                log.error("Unexpected MCP audit failure", e);
            }
        }
    }

    private ResponseEntity<JsonNode> response(JsonNode id, JsonNode result) {
        ObjectNode out = mapper.createObjectNode(); out.put("jsonrpc", "2.0"); out.set("id", id); out.set("result", result); return ResponseEntity.ok(out);
    }
    private ResponseEntity<JsonNode> error(JsonNode id, int code, String message) {
        ObjectNode out = mapper.createObjectNode(); out.put("jsonrpc", "2.0"); if (id == null) out.putNull("id"); else out.set("id", id);
        out.putObject("error").put("code", code).put("message", message == null ? "Error" : message); return ResponseEntity.ok(out);
    }
    private JsonNode initialize(JsonNode params) {
        if (params == null || !params.isObject()) throw rpc(-32602, "Invalid initialization parameters");
        JsonNode version = params.get("protocolVersion"), capabilities = params.get("capabilities"), clientInfo = params.get("clientInfo");
        if (version == null || !version.isTextual() || version.textValue().isBlank()
                || capabilities == null || !capabilities.isObject() || clientInfo == null || !clientInfo.isObject()
                || !clientInfo.path("name").isTextual() || clientInfo.path("name").asText().isBlank()
                || !clientInfo.path("version").isTextual() || clientInfo.path("version").asText().isBlank())
            throw rpc(-32602, "Invalid initialization parameters");
        ObjectNode r = mapper.createObjectNode(); r.put("protocolVersion", PROTOCOL_VERSION); r.putObject("capabilities").putObject("tools"); r.putObject("serverInfo").put("name", "middleproject-reminder").put("version", "1.0"); return r;
    }
    private JsonNode tools() {
        ArrayNode a = mapper.createArrayNode();
        for (String name : TOOL_NAMES) {
            ObjectNode t = a.addObject();
            t.put("name", name);
            t.put("description", TOOL_DESCRIPTIONS.get(name));
            t.set("inputSchema", schema(name));
            ObjectNode annotations = t.putObject("annotations");
            annotations.put("title", TOOL_TITLES.get(name));
            annotations.put("readOnlyHint", READ_ONLY_TOOLS.contains(name));
            annotations.put("destructiveHint", DESTRUCTIVE_TOOLS.contains(name));
            annotations.put("idempotentHint", READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name));
            annotations.put("openWorldHint", OPEN_WORLD_TOOLS.contains(name));
        }
        ObjectNode r = mapper.createObjectNode();
        r.set("tools", a);
        return r;
    }
    private ObjectNode schema(String name) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (String argument : TOOL_ARGS.get(name)) schemaField(name, argument, properties.putObject(argument));
        ArrayNode required = schema.putArray("required");
        for (String argument : TOOL_ARGS.get(name)) if (!isOptional(name, argument)) required.add(argument);
        schema.put("additionalProperties", false);
        return schema;
    }

    private void schemaField(String tool, String argument, ObjectNode field) {
        if (Set.of("expectedVersion", "reminderLeadMinutes", "maxCandidates", "pageNo", "numOfRows", "limit", "cityCode", "radiusMeters").contains(argument)) {
            field.put("type", "integer");
            field.put("minimum", "expectedVersion".equals(argument) ? 0 : 1);
            if ("reminderLeadMinutes".equals(argument)) { field.put("minimum", PrivateCarPlanningInput.LEAD_MIN); field.put("maximum", PrivateCarPlanningInput.LEAD_MAX); }
            if ("maxCandidates".equals(argument)) field.put("maximum", 3);
            if ("radiusMeters".equals(argument)) field.put("maximum", 20_000);
            if ("limit".equals(argument)) field.put("maximum", "resolve_place".equals(tool) ? 15 : 100);
            if ("numOfRows".equals(argument)) field.put("maximum", Set.of("search_subway_stations", "find_nearby_bus_stops", "get_bus_arrivals", "search_bus_routes").contains(tool) ? 50 : 100);
        } else if (Set.of("gpsLati", "latitude").contains(argument)) {
            field.put("type", "number"); field.put("minimum", 33.0); field.put("maximum", 39.0);
        } else if (Set.of("gpsLong", "longitude").contains(argument)) {
            field.put("type", "number"); field.put("minimum", 124.0); field.put("maximum", 132.0);
        } else if ("depPlandTime".equals(argument)) {
            field.put("type", "string"); field.put("pattern", "^[0-9]{8}$"); field.put("minLength", 8); field.put("maxLength", 8);
        } else if ("dailyTypeCode".equals(argument) || "upDownTypeCode".equals(argument) || "departureTiming".equals(argument) || "sort".equals(argument)) {
            field.put("type", "string");
            ArrayNode values = field.putArray("enum");
            List<String> allowed = "dailyTypeCode".equals(argument) ? DAILY_TYPE_CODES : "upDownTypeCode".equals(argument) ? UP_DOWN_TYPE_CODES : "departureTiming".equals(argument) ? DEPARTURE_TIMINGS : RECOMMENDATION_SORTS;
            allowed.forEach(values::add);
        } else if ("accepted".equals(argument)) {
            field.put("type", "boolean");
        } else {
            field.put("type", "string");
            if ("proposalId".equals(argument)) { field.put("pattern", PROPOSAL_ID_PATTERN); field.put("minLength", 64); field.put("maxLength", 64); }
            if (Set.of("subwayStationName", "landmark", "query", "origin", "destination", "placeName").contains(argument)) { field.put("minLength", 1); field.put("maxLength", 200); }
            if ("alias".equals(argument)) { field.put("minLength", 1); field.put("maxLength", 100); }
            if ("address".equals(argument)) field.put("maxLength", 300);
            if (argument.endsWith("Id") && !Set.of("proposalId", "confirmationId", "subwayStationId", "nodeId", "depTerminalId", "arrTerminalId").contains(argument)) field.put("format", "uuid");
            if ("idempotencyKey".equals(argument)) { field.put("minLength", 1); field.put("maxLength", 200); }
            if (Set.of("departureAt", "returnAt", "previewFetchedAt").contains(argument)) field.put("format", "date-time");
        }
    }

    private static boolean isOptional(String tool, String argument) {
        if ("create_trip_draft".equals(tool) && "returnAt".equals(argument)) return true;
        if ("get_realtime_subway_arrivals".equals(tool) && "limit".equals(argument)) return true;
        if ("resolve_place".equals(tool) && "limit".equals(argument)) return true;
        if ("find_nearby_subway_stations".equals(tool) && "radiusMeters".equals(argument)) return true;
        if (Set.of("get_subway_station_schedule", "find_nearby_bus_stops", "get_bus_arrivals", "search_bus_routes", "get_express_bus_arrivals", "get_intercity_bus_schedule").contains(tool)
                && Set.of("pageNo", "numOfRows").contains(argument)) return true;
        return "find_bus_stops_by_landmark".equals(tool) && "maxCandidates".equals(argument);
    }
    private ObjectNode toolResult(Object result) throws Exception {
        JsonNode structured = result instanceof TransportOutcome<?> outcome
                ? transportOutcomeNode(outcome)
                : mapper.valueToTree(result);
        ObjectNode response = mapper.createObjectNode();
        response.putArray("content").addObject()
                .put("type", "text")
                .put("text", mapper.writeValueAsString(structured));
        response.put("isError", false);
        response.set("structuredContent", structured);
        return response;
    }

    private ObjectNode transportOutcomeNode(TransportOutcome<?> outcome) {
        ObjectNode node = mapper.createObjectNode();
        node.put("success", outcome.isSuccess());
        node.put("empty", outcome.isEmpty());
        node.put("retryable", outcome.isRetryable());
        if (outcome.isSuccess()) {
            node.set("value", mapper.valueToTree(outcome.value()));
            node.putNull("failureKind");
            node.putNull("errorMessage");
        } else if (outcome.isEmpty()) {
            node.putArray("value");
            node.putNull("failureKind");
            node.putNull("errorMessage");
        } else {
            node.putNull("value");
            node.put("failureKind", outcome.failureKind().name());
            node.put("errorMessage", outcome.errorMessage());
        }
        return node;
    }

    private String owner() { return demoOwner.ownerId(); }
    private void validate(String name, JsonNode args) {
        if (!TOOL_ARGS.containsKey(name) || args == null || !args.isObject()) throw rpc(-32602, "Unknown tool or arguments");
        Set<String> allowed = new HashSet<>(TOOL_ARGS.get(name));
        args.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw rpc(-32602, "Unknown argument"); });
        for (String argument : TOOL_ARGS.get(name)) {
            JsonNode value = args.get(argument);
            if (value == null || value.isNull()) {
                if (isOptional(name, argument)) continue;
                throw rpc(-32602, "Missing argument");
            }
            validateArgument(name, argument, value);
        }
    }

    private void validateArgument(String tool, String argument, JsonNode value) {
        if ("expectedVersion".equals(argument)) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw rpc(-32602, "Invalid version");
        } else if ("reminderLeadMinutes".equals(argument)) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < PrivateCarPlanningInput.LEAD_MIN || value.longValue() > PrivateCarPlanningInput.LEAD_MAX) throw rpc(-32602, "Invalid reminder lead");
        } else if ("pageNo".equals(argument)) {
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) throw rpc(-32602, "Invalid pageNo");
        } else if ("numOfRows".equals(argument)) {
            int maximum = Set.of("search_subway_stations", "find_nearby_bus_stops", "get_bus_arrivals", "search_bus_routes").contains(tool) ? 50 : 100;
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > maximum) throw rpc(-32602, "Invalid numOfRows");
        } else if ("limit".equals(argument)) {
            int maximum = "resolve_place".equals(tool) ? 15 : 100;
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > maximum) throw rpc(-32602, "Invalid limit");
        } else if ("radiusMeters".equals(argument)) {
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > 20_000) throw rpc(-32602, "Invalid radiusMeters");
        } else if ("cityCode".equals(argument)) {
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) throw rpc(-32602, "Invalid cityCode");
        } else if (Set.of("gpsLati", "latitude").contains(argument)) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 33.0 || value.doubleValue() > 39.0) throw rpc(-32602, "Invalid latitude");
        } else if (Set.of("gpsLong", "longitude").contains(argument)) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 124.0 || value.doubleValue() > 132.0) throw rpc(-32602, "Invalid longitude");
        } else if ("depPlandTime".equals(argument)) {
            if (!value.isTextual() || !value.textValue().matches("^[0-9]{8}$")) throw rpc(-32602, "Invalid depPlandTime");
            try { LocalDate.parse(value.textValue(), STRICT_DATE_FMT); } catch (Exception e) { throw rpc(-32602, "Invalid depPlandTime"); }
        } else if (Set.of("dailyTypeCode", "upDownTypeCode", "departureTiming", "sort").contains(argument)) {
            List<String> allowed = "dailyTypeCode".equals(argument) ? DAILY_TYPE_CODES : "upDownTypeCode".equals(argument) ? UP_DOWN_TYPE_CODES : "departureTiming".equals(argument) ? DEPARTURE_TIMINGS : RECOMMENDATION_SORTS;
            if (!value.isTextual() || !allowed.contains(value.textValue())) throw rpc(-32602, "Invalid " + argument);
        } else if ("proposalId".equals(argument)) {
            if (!value.isTextual() || !value.textValue().matches(PROPOSAL_ID_PATTERN)) throw rpc(-32602, "Invalid proposal id");
        } else if ("accepted".equals(argument)) {
            if (!value.isBoolean()) throw rpc(-32602, "Invalid accepted");
        } else {
            validateTextArgument(argument, value);
        }
        if (argument.endsWith("Id") && !Set.of("proposalId", "confirmationId", "subwayStationId", "nodeId", "depTerminalId", "arrTerminalId").contains(argument)) {
            try { UUID.fromString(value.textValue()); } catch (Exception e) { throw rpc(-32602, "Invalid UUID"); }
        }
        if (Set.of("departureAt", "returnAt", "previewFetchedAt").contains(argument)) {
            try { OffsetDateTime.parse(value.textValue()); } catch (Exception e) { throw rpc(-32602, "Invalid date-time"); }
        }
    }

    private void validateTextArgument(String argument, JsonNode value) {
        if (!value.isTextual()) throw rpc(-32602, "Invalid argument");
        String text = value.textValue();
        if ("idempotencyKey".equals(argument) && (text.isBlank() || text.length() > 200)) throw rpc(-32602, "Invalid argument");
        if (Set.of("subwayStationName", "stationName", "subwayStationId", "nodeId", "routeNo", "depTerminalCode", "arrTerminalCode", "depTerminalId", "arrTerminalId", "landmark", "query", "origin", "destination", "placeName", "alias").contains(argument) && text.isBlank()) throw rpc(-32602, "Invalid " + argument);
        int maximum = "alias".equals(argument) ? 100 : "address".equals(argument) ? 300 : 200;
        if (Set.of("subwayStationName", "landmark", "query", "origin", "destination", "placeName", "alias", "address").contains(argument) && text.length() > maximum) throw rpc(-32602, "Invalid " + argument);
    }
    private Object call(String n, JsonNode a) {
        if(n.equals("list_origin_favorites"))return originFavorites.list(owner());
        if(n.equals("save_origin_favorite"))return originFavorites.save(owner(),a.get("alias").textValue(),a.get("placeName").textValue(),a.get("address").textValue(),a.get("latitude").doubleValue(),a.get("longitude").doubleValue(),a.get("idempotencyKey").textValue());
        if(n.equals("delete_origin_favorite"))return originFavorites.delete(owner(),UUID.fromString(a.get("originFavoriteId").textValue()),a.get("idempotencyKey").textValue());
        if(n.equals("preview_public_transit_route"))return transitRoutePreview.preview(a.get("origin").textValue(),a.get("destination").textValue());
        if(n.equals("find_nearby_subway_stations"))return places.nearbySubway(a.get("latitude").doubleValue(),a.get("longitude").doubleValue(),a.has("radiusMeters")&&!a.get("radiusMeters").isNull()?a.get("radiusMeters").intValue():1_000);
        if(n.equals("resolve_place"))return places.resolve(a.get("query").textValue(),a.has("limit")&&!a.get("limit").isNull()?a.get("limit").intValue():3);
        if(n.equals("list_transit_favorites"))return transitFavorites.list(owner());
        if(n.equals("save_subway_favorite"))return transitFavorites.saveSubway(owner(),a.get("alias").textValue(),a.get("stationName").textValue(),a.get("idempotencyKey").textValue());
        if(n.equals("save_bus_favorite"))return transitFavorites.saveBus(owner(),a.get("alias").textValue(),a.get("cityCode").intValue(),a.get("nodeId").textValue(),a.get("stopName").textValue(),a.get("routeNo").textValue(),a.get("idempotencyKey").textValue());
        if(n.equals("find_bus_stops_by_landmark"))return landmarkBusStops.find(a.get("landmark").textValue(),3);
        if(n.equals("get_intercity_bus_schedule"))return publicTransport.getIntercityBusSchedule(a.get("depTerminalId").textValue(),a.get("arrTerminalId").textValue(),a.get("depPlandTime").textValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("get_express_bus_arrivals"))return publicTransport.getExpressBusArrivals(a.get("depTerminalCode").textValue(),a.get("arrTerminalCode").textValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("search_bus_routes"))return publicTransport.searchBusRoutes(a.get("cityCode").intValue(),a.get("routeNo").textValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("get_bus_arrivals"))return publicTransport.getBusArrivals(a.get("cityCode").intValue(),a.get("nodeId").textValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("find_nearby_bus_stops"))return publicTransport.getNearbyBusStops(a.get("gpsLati").doubleValue(),a.get("gpsLong").doubleValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("get_subway_station_schedule"))return publicTransport.getSubwayStationSchedule(a.get("subwayStationId").textValue(),a.get("dailyTypeCode").textValue(),a.get("upDownTypeCode").textValue(),a.has("pageNo")&&!a.get("pageNo").isNull()?a.get("pageNo").intValue():1,a.has("numOfRows")&&!a.get("numOfRows").isNull()?a.get("numOfRows").intValue():20);
        if(n.equals("get_realtime_subway_arrivals"))return publicTransport.getRealtimeSubwayArrivals(a.get("stationName").textValue(),a.has("limit")&&!a.get("limit").isNull()?a.get("limit").intValue():20);
        if(n.equals("search_subway_stations"))return publicTransport.searchSubwayStations(a.get("subwayStationName").textValue(),a.get("pageNo").intValue(),a.get("numOfRows").intValue());
        if(n.equals("create_trip_draft"))return trips.createDraft(a.get("departure").textValue(),a.get("destination").textValue(),OffsetDateTime.parse(a.get("departureAt").textValue()),a.get("returnAt")==null||a.get("returnAt").isNull()?null:OffsetDateTime.parse(a.get("returnAt").textValue()),a.get("idempotencyKey").textValue());
        if(n.equals("answer_trip_question"))return trips.answerQuestion(UUID.fromString(a.get("tripId").textValue()),a.get("question").textValue(),a.get("answer").textValue(),a.get("idempotencyKey").textValue());
        if(n.equals("confirm_trip"))return trips.confirm(UUID.fromString(a.get("tripId").textValue()),a.get("confirmation").textValue(),a.get("idempotencyKey").textValue());
        if(n.equals("cancel_trip"))return trips.cancel(UUID.fromString(a.get("tripId").textValue()),a.get("expectedVersion").longValue(),a.get("idempotencyKey").textValue());
        if(n.equals("next_private_car_question"))return privateCar.nextQuestion(UUID.fromString(a.get("tripId").textValue()));
        if(n.equals("preview_private_car_route"))return privateCar.previewRoute(UUID.fromString(a.get("tripId").textValue()));
        if(n.equals("confirm_private_car_route"))return privateCar.confirmRoute(UUID.fromString(a.get("tripId").textValue()),a.get("proposalId").textValue(),OffsetDateTime.parse(a.get("previewFetchedAt").textValue()),a.get("reminderLeadMinutes").intValue(),a.get("confirmationId").textValue(),a.get("idempotencyKey").textValue());
        if(n.equals("get_trip_travel_context"))return travel.context(UUID.fromString(a.get("tripId").textValue()),DepartureTiming.valueOf(a.get("departureTiming").textValue()),RecommendationSort.valueOf(a.get("sort").textValue()));
        if(n.equals("record_trip_followup_consent"))return travel.recordConsent(UUID.fromString(a.get("tripId").textValue()),a.get("accepted").asBoolean(),a.get("idempotencyKey").textValue());
        if(n.equals("get_trip_recommendations"))return travel.recommend(UUID.fromString(a.get("tripId").textValue()),RecommendationSort.valueOf(a.get("sort").textValue()));
        if(n.equals("create_device_pairing_code"))return devicePairing.issueCode();
        if(n.equals("create_reminder"))return reminders.create(UUID.fromString(a.get("eventId").textValue()),UUID.fromString(a.get("policyId").textValue()),a.get("idempotencyKey").textValue(),owner());
        if(n.equals("list_reminders"))return reminders.all(owner());
        UUID id=UUID.fromString(a.get("reminderId").textValue());
        Reminder owned=reminders.find(id,owner());
        if(n.equals("get_reminder"))return owned;
        if(n.equals("update_reminder"))return reminders.update(id,UUID.fromString(a.get("eventId").textValue()),UUID.fromString(a.get("policyId").textValue()),a.get("expectedVersion").longValue(),a.get("idempotencyKey").textValue(),owner());
        if(n.equals("cancel_reminder"))return reminders.transition(id,ReminderStatus.CANCELLED,a.get("expectedVersion").longValue(),a.get("idempotencyKey").textValue(),owner());
        return queries.deliveryStatus(owned.id()); }
    private McpException rpc(int code,String message){return new McpException(code,message);} private static class McpException extends RuntimeException{final int code;McpException(int c,String m){super(m);code=c;}}
}
