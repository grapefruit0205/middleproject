package com.middleproject.reminder.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.middleproject.reminder.application.McpAuditService;
import com.middleproject.reminder.application.McpReminderQueryService;
import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.TravelRecommendationService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.device.DevicePairingService;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/mcp")
public class McpAdapterController {
    private static final List<String> TOOL_NAMES = List.of("create_reminder", "list_reminders", "get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status",
            "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
            "next_private_car_question", "preview_private_car_route", "confirm_private_car_route",
            "get_trip_travel_context", "record_trip_followup_consent", "get_trip_recommendations",
            "create_device_pairing_code");
    private static final List<String> DEPARTURE_TIMINGS = List.of("SAME_DAY", "PREVIOUS_DAY");
    private static final List<String> RECOMMENDATION_SORTS = List.of("DISTANCE", "PRICE", "RATING");
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
            Map.entry("create_device_pairing_code", List.of()));

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
            Map.entry("create_device_pairing_code", "Create a one-time device pairing code"));

    /** MCP-standard top-level tool descriptions (model-readable, distinct from annotations.title). */
    private static final Map<String, String> TOOL_DESCRIPTIONS = Map.ofEntries(
            Map.entry("create_reminder", "Creates one reminder for an existing event and notification policy in the demo owner's scope. Replaying the same idempotencyKey with the same payload returns the stored result without creating a duplicate reminder."),
            Map.entry("list_reminders", "Lists all reminders owned by the deployment-fixed demo owner. Read-only."),
            Map.entry("get_reminder", "Returns one reminder by id when it is owned by the demo owner; other reminders are denied without leaking their existence. Read-only."),
            Map.entry("update_reminder", "Updates the event and policy of an existing demo-owner reminder with an optimistic expectedVersion and an idempotencyKey; replaying the same key returns the stored update."),
            Map.entry("cancel_reminder", "Cancels an existing demo-owner reminder with an optimistic expectedVersion and an idempotencyKey. Cancellation is destructive and must be explicitly confirmed by the user."),
            Map.entry("get_delivery_status", "Returns delivery attempts and receipts for one demo-owner reminder. Read-only."),
            Map.entry("create_trip_draft", "Creates a business trip draft with departure, destination, and departure date/time; returnAt is optional for a draft. Protected by an idempotencyKey."),
            Map.entry("answer_trip_question", "Records the answer to the next planning question for a trip draft. Protected by an idempotencyKey."),
            Map.entry("confirm_trip", "Confirms a trip draft so its reminder is scheduled; replaying the same idempotencyKey returns the stored confirmation."),
            Map.entry("cancel_trip", "Cancels a trip with an optimistic expectedVersion and an idempotencyKey. Cancellation is destructive and must be explicitly confirmed by the user."),
            Map.entry("next_private_car_question", "Returns the next missing private-car planning question for an existing trip, or none when the private-car inputs are complete. Read-only."),
            Map.entry("preview_private_car_route", "Previews a private-car route for an existing trip without persisting anything. Read-only."),
            Map.entry("confirm_private_car_route", "Confirms a previously previewed private-car route, scheduling its reminder. Protected by an idempotencyKey and the preview proposalId."),
            Map.entry("get_trip_travel_context", "Gets weather, packing, and accommodation context for a trip from travel providers, inserting a PROPOSED follow-up consent row when the trip has none. Repeat-safe: an existing ACCEPTED/DECLINED consent is never overwritten."),
            Map.entry("record_trip_followup_consent", "Records the user's ACCEPTED or DECLINED decision on follow-up recommendations for a trip. Protected by an idempotencyKey."),
            Map.entry("get_trip_recommendations", "Returns follow-up recommendations for a trip, gated on ACCEPTED consent and sorted by DISTANCE, PRICE, or RATING. Read-only."),
            Map.entry("create_device_pairing_code", "Issues one 5-minute one-time pairing code for the fixed demo owner so an Android companion can exchange it for a device token. The code is returned only once and only its salted hash is stored, so a second call while the code is active fails safely with a conflict. It is a mutating, non-destructive, non-idempotent operation: at most one active code exists at any time."));

    /** True when the tool only reads state and never mutates anything. */
    private static final Set<String> READ_ONLY_TOOLS = Set.of("list_reminders", "get_reminder", "get_delivery_status",
            "next_private_car_question", "preview_private_car_route", "get_trip_recommendations");
    /** Destructive writes: cancelling state that cannot be restored by the same tool. */
    private static final Set<String> DESTRUCTIVE_TOOLS = Set.of("cancel_reminder", "cancel_trip");
    /**
     * Repeat-safe tools: replaying the same arguments (including the idempotency key) is safe.
     * Every write that is protected by an idempotencyKey is replayable by contract;
     * get_trip_travel_context may insert a PROPOSED consent row but is repeat-safe because
     * an existing ACCEPTED/DECLINED status is never overwritten.
     */
    private static final Set<String> IDEMPOTENT_TOOLS = Set.of("create_reminder", "update_reminder", "cancel_reminder",
            "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
            "confirm_private_car_route", "get_trip_travel_context", "record_trip_followup_consent");
    /** Open-world tools: their results depend on external travel providers. */
    private static final Set<String> OPEN_WORLD_TOOLS = Set.of("get_trip_travel_context", "get_trip_recommendations",
            "preview_private_car_route", "confirm_private_car_route");

    private final ReminderService reminders;
    private final McpReminderQueryService queries;
    private final McpAuditService audit;
    private final TripService trips;
    private final PrivateCarPlanningService privateCar;
    private final TravelRecommendationService travel;
    private final DevicePairingService devicePairing;
    private final DemoOwnerContext demoOwner;
    private static final Logger log = LoggerFactory.getLogger(McpAdapterController.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";
    static final String PROPOSAL_ID_PATTERN = "[0-9a-f]{64}";
    private final ObjectMapper mapper;

    public McpAdapterController(ReminderService reminders, McpReminderQueryService queries, McpAuditService audit, TripService trips, PrivateCarPlanningService privateCar, TravelRecommendationService travel, DemoOwnerContext demoOwner, ObjectMapper mapper, DevicePairingService devicePairing) {
        this.reminders = reminders; this.queries = queries; this.audit = audit; this.trips = trips; this.privateCar = privateCar; this.travel = travel; this.demoOwner = demoOwner; this.mapper = mapper; this.devicePairing = devicePairing;
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
    private ObjectNode schema(String name) { ObjectNode s = mapper.createObjectNode(); s.put("type", "object"); ObjectNode p = s.putObject("properties"); for (String arg : TOOL_ARGS.get(name)) { ObjectNode field = p.putObject(arg); if ("expectedVersion".equals(arg)) { field.put("type", "integer"); field.put("minimum", 0); } else if ("reminderLeadMinutes".equals(arg)) { field.put("type", "integer"); field.put("minimum", PrivateCarPlanningInput.LEAD_MIN); field.put("maximum", PrivateCarPlanningInput.LEAD_MAX); } else if ("accepted".equals(arg)) { field.put("type", "boolean"); } else if ("departureTiming".equals(arg)) { field.put("type", "string"); ArrayNode enums = field.putArray("enum"); for (String e : DEPARTURE_TIMINGS) enums.add(e); } else if ("sort".equals(arg)) { field.put("type", "string"); ArrayNode enums = field.putArray("enum"); for (String e : RECOMMENDATION_SORTS) enums.add(e); } else if ("proposalId".equals(arg)) { field.put("type", "string"); field.put("pattern", PROPOSAL_ID_PATTERN); field.put("minLength", 64); field.put("maxLength", 64); } else { field.put("type", "string"); if (arg.endsWith("Id") && !"confirmationId".equals(arg)) field.put("format", "uuid"); if ("idempotencyKey".equals(arg)) { field.put("minLength", 1); field.put("maxLength", 200); } if ("departureAt".equals(arg) || "returnAt".equals(arg) || "previewFetchedAt".equals(arg)) field.put("format", "date-time"); } } ArrayNode required = s.putArray("required"); for (String arg : TOOL_ARGS.get(name)) { if ("create_trip_draft".equals(name) && "returnAt".equals(arg)) continue; required.add(arg); } s.put("additionalProperties", false); return s; }
    private ObjectNode toolResult(Object result) throws Exception { ObjectNode r = mapper.createObjectNode(); ArrayNode content = r.putArray("content"); content.addObject().put("type", "text").put("text", mapper.writeValueAsString(result)); r.put("isError", false); r.set("structuredContent", mapper.valueToTree(result)); return r; }

    private String owner() { return demoOwner.ownerId(); }
    private void validate(String name, JsonNode args) { if (!TOOL_ARGS.containsKey(name) || args == null || !args.isObject()) throw rpc(-32602, "Unknown tool or arguments"); Set<String> allowed = new HashSet<>(TOOL_ARGS.get(name)); args.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw rpc(-32602, "Unknown argument"); }); for (String k : TOOL_ARGS.get(name)) { JsonNode v=args.get(k); if(v==null||v.isNull()){ if ("create_trip_draft".equals(name) && "returnAt".equals(k)) continue; throw rpc(-32602,"Missing argument"); } if("expectedVersion".equals(k)){if(!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<0)throw rpc(-32602,"Invalid version");} else if("reminderLeadMinutes".equals(k)){if(!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<PrivateCarPlanningInput.LEAD_MIN||v.longValue()>PrivateCarPlanningInput.LEAD_MAX)throw rpc(-32602,"Invalid reminder lead");} else if("proposalId".equals(k)){if(!v.isTextual()||!v.textValue().matches(PROPOSAL_ID_PATTERN))throw rpc(-32602,"Invalid proposal id");} else if("accepted".equals(k)){if(!v.isBoolean())throw rpc(-32602,"Invalid accepted");} else if("departureTiming".equals(k)){if(!v.isTextual()||!DEPARTURE_TIMINGS.contains(v.textValue()))throw rpc(-32602,"Invalid departure timing");} else if("sort".equals(k)){if(!v.isTextual()||!RECOMMENDATION_SORTS.contains(v.textValue()))throw rpc(-32602,"Invalid sort");} else if(!v.isTextual() || ("idempotencyKey".equals(k) && (v.textValue().isBlank() || v.textValue().length()>200))) throw rpc(-32602,"Invalid argument"); if(k.endsWith("Id") && !"proposalId".equals(k) && !"confirmationId".equals(k)) try{UUID.fromString(v.textValue());}catch(Exception e){throw rpc(-32602,"Invalid UUID");} if(("departureAt".equals(k)||"returnAt".equals(k)||"previewFetchedAt".equals(k))) try{OffsetDateTime.parse(v.textValue());}catch(Exception e){throw rpc(-32602,"Invalid date-time");} } }
    private Object call(String n, JsonNode a) {
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
