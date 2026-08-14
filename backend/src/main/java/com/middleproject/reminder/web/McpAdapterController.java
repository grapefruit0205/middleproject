package com.middleproject.reminder.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.middleproject.reminder.application.McpAuditService;
import com.middleproject.reminder.application.McpReminderQueryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/mcp")
public class McpAdapterController {
    private static final List<String> TOOL_NAMES = List.of("create_reminder", "list_reminders", "get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status");
    private static final Map<String, List<String>> TOOL_ARGS = Map.of(
            "create_reminder", List.of("eventId", "policyId", "idempotencyKey"),
            "list_reminders", List.of(), "get_reminder", List.of("reminderId"),
            "update_reminder", List.of("reminderId", "eventId", "policyId", "expectedVersion", "idempotencyKey"),
            "cancel_reminder", List.of("reminderId", "expectedVersion", "idempotencyKey"),
            "get_delivery_status", List.of("reminderId"));

    private final ReminderService reminders;
    private final McpReminderQueryService queries;
    private final McpAuditService audit;
    private static final Logger log = LoggerFactory.getLogger(McpAdapterController.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private final ObjectMapper mapper;

    public McpAdapterController(ReminderService reminders, McpReminderQueryService queries, McpAuditService audit, ObjectMapper mapper) {
        this.reminders = reminders; this.queries = queries; this.audit = audit; this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(Principal principal,
                                           @RequestBody(required = false) String body) {
        JsonNode request = null, id = null; String user = null, method = null, toolName = null; UUID reminderId = null; String outcome = "FAILED";
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
            user = identity(principal);
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
            Object result = call(tool, args, user);
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
                audit.record(user, toolName != null ? toolName : method, id == null ? null : id.asText(), outcome, reminderId);
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
    private JsonNode tools() { ArrayNode a = mapper.createArrayNode(); for (String name : TOOL_NAMES) { ObjectNode t = a.addObject(); t.put("name", name); t.set("inputSchema", schema(name)); } ObjectNode r = mapper.createObjectNode(); r.set("tools", a); return r; }
    private ObjectNode schema(String name) { ObjectNode s = mapper.createObjectNode(); s.put("type", "object"); ObjectNode p = s.putObject("properties"); for (String arg : TOOL_ARGS.get(name)) { ObjectNode field = p.putObject(arg); if ("expectedVersion".equals(arg)) { field.put("type", "integer"); field.put("minimum", 0); } else { field.put("type", "string"); if (arg.endsWith("Id")) field.put("format", "uuid"); if ("idempotencyKey".equals(arg)) { field.put("minLength", 1); field.put("maxLength", 200); } } } ArrayNode required = s.putArray("required"); TOOL_ARGS.get(name).forEach(required::add); s.put("additionalProperties", false); return s; }
    private ObjectNode toolResult(Object result) throws Exception { ObjectNode r = mapper.createObjectNode(); ArrayNode content = r.putArray("content"); content.addObject().put("type", "text").put("text", mapper.writeValueAsString(result)); r.put("isError", false); r.set("structuredContent", mapper.valueToTree(result)); return r; }

    private String identity(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank() || principal.getName().length() > 200)
            throw rpc(-32001, "Authenticated user identity is required");
        return principal.getName();
    }
    private void validate(String name, JsonNode args) { if (!TOOL_ARGS.containsKey(name) || args == null || !args.isObject()) throw rpc(-32602, "Unknown tool or arguments"); Set<String> allowed = new HashSet<>(TOOL_ARGS.get(name)); args.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw rpc(-32602, "Unknown argument"); }); for (String k : TOOL_ARGS.get(name)) { JsonNode v=args.get(k); if(v==null||v.isNull()) throw rpc(-32602,"Missing argument"); if("expectedVersion".equals(k)){if(!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<0)throw rpc(-32602,"Invalid version");} else if(!v.isTextual() || ("idempotencyKey".equals(k) && (v.textValue().isBlank() || v.textValue().length()>200))) throw rpc(-32602,"Invalid argument"); if(k.endsWith("Id")) try{UUID.fromString(v.textValue());}catch(Exception e){throw rpc(-32602,"Invalid UUID");} } }
    private Object call(String n, JsonNode a, String u) { if(n.equals("create_reminder"))return reminders.create(UUID.fromString(a.get("eventId").textValue()),UUID.fromString(a.get("policyId").textValue()),a.get("idempotencyKey").textValue(),u); if(n.equals("list_reminders"))return reminders.all(u); UUID id=UUID.fromString(a.get("reminderId").textValue()); Reminder owned=reminders.find(id,u); if(n.equals("get_reminder"))return owned; if(n.equals("update_reminder"))return reminders.update(id,UUID.fromString(a.get("eventId").textValue()),UUID.fromString(a.get("policyId").textValue()),a.get("expectedVersion").longValue(),a.get("idempotencyKey").textValue(),u); if(n.equals("cancel_reminder"))return reminders.transition(id,ReminderStatus.CANCELLED,a.get("expectedVersion").longValue(),a.get("idempotencyKey").textValue(),u); return queries.deliveryStatus(owned.id()); }
    private McpException rpc(int code,String message){return new McpException(code,message);} private static class McpException extends RuntimeException{final int code;McpException(int c,String m){super(m);code=c;}}
}
