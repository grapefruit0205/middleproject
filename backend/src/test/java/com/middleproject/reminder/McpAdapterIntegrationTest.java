package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.EventService;
import com.middleproject.reminder.application.McpReminderQueryService;
import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.domain.Reminder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mcp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true", "trip.demo-owner-id=demo-owner"})
@AutoConfigureMockMvc
class McpAdapterIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EventService events;
    @Autowired PolicyService policies;
    @Autowired ReminderService reminders;
    @Autowired JdbcTemplate db;
    @SpyBean McpReminderQueryService queries;

    @BeforeEach void clean() {
        reset(queries);
        db.update("delete from mcp_audit");
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @Test void initializeAndToolsListExposeExactlySixClosedSchemas() throws Exception {
        JsonNode init = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", "alice");
        assertEquals("2.0", init.path("jsonrpc").asText());
        assertEquals("2025-03-26", init.path("result").path("protocolVersion").asText());

        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", "alice").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        assertEquals(Set.of("create_reminder", "list_reminders", "get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status",
                "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
                "next_private_car_question", "preview_private_car_route", "confirm_private_car_route"), names);
        assertEquals(13, tools.size());
        for (JsonNode tool : tools) {
            JsonNode schema = tool.path("inputSchema");
            assertEquals("object", schema.path("type").asText());
            assertFalse(schema.path("additionalProperties").asBoolean());
            if (tool.path("name").asText().equals("create_trip_draft")) {
                // returnAt is optional for a draft because the return time may not be known yet
                assertEquals(schema.path("properties").size() - 1, schema.path("required").size());
                assertTrue(schema.path("required").toString().contains("returnAt") == false);
            } else {
                assertEquals(schema.path("properties").size(), schema.path("required").size());
            }
            schema.path("properties").fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (field.getKey().equals("expectedVersion")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(0, value.path("minimum").asInt());
                } else if (field.getKey().equals("reminderLeadMinutes")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(0, value.path("minimum").asInt());
                    assertEquals(1440, value.path("maximum").asInt());
                } else {
                    assertEquals("string", value.path("type").asText());
                    if (field.getKey().endsWith("Id")
                            && !field.getKey().equals("proposalId")
                            && !field.getKey().equals("confirmationId")) {
                        assertEquals("uuid", value.path("format").asText());
                    }
                    // proposalId is a SHA-256 digest and confirmationId is an arbitrary
                    // client-chosen string; neither is a UUID, so they must not claim one.
                    if (field.getKey().equals("proposalId")) {
                        assertEquals("[0-9a-f]{64}", value.path("pattern").asText());
                        assertEquals(64, value.path("minLength").asInt());
                        assertEquals(64, value.path("maxLength").asInt());
                        assertFalse(value.has("format"), "proposalId must not be marked as a UUID");
                    }
                    if (field.getKey().equals("confirmationId")) {
                        assertFalse(value.has("format"), "confirmationId must not be marked as a UUID");
                    }
                    if (field.getKey().equals("idempotencyKey")) {
                        assertEquals(1, value.path("minLength").asInt());
                        assertEquals(200, value.path("maxLength").asInt());
                    }
                    if (field.getKey().equals("previewFetchedAt")) assertEquals("date-time", value.path("format").asText());
                }
            });
        }
    }

    @Test void initializeNegotiatesVersionAndRejectsMalformedParameters() throws Exception {
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", -32602, "alice");
        assertEquals("2025-03-26", call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", "alice").path("result").path("protocolVersion").asText());
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":[],\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", -32602, "alice");
        assertEquals("2025-03-26", call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", "alice").path("result").path("protocolVersion").asText());
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":[]}}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"\",\"version\":\"1\"}}}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\" \"}}}", -32602, "alice");
    }

    @Test void initializedNotificationIsAcceptedWithoutAJsonRpcResponse() throws Exception {
        mvc.perform(post("/api/mcp")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isNoContent());
    }

    @Test void unexpectedToolFailureReturnsSanitizedJsonRpcError() throws Exception {
        doThrow(new IllegalStateException("sentinel-unexpected-application-failure"))
                .when(queries).deliveryStatus(org.mockito.ArgumentMatchers.any());
        JsonNode response = call(tool("get_delivery_status", "{\"reminderId\":\"" + seed().alice().id() + "\"}"), "alice");
        assertEquals(-32000, response.path("error").path("code").asInt());
        assertEquals("Tool execution failed", response.path("error").path("message").asText());
        assertFalse(response.toString().contains("sentinel-unexpected-application-failure"));
    }

    @Test void longJsonRpcIdentifiersAndRejectedToolNamesRemainAudited() throws Exception {
        String requestId = "r".repeat(500);
        call("{\"jsonrpc\":\"2.0\",\"id\":\"" + requestId + "\",\"method\":\"tools/list\"}", "alice");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where request_id=?", Integer.class, requestId));

        String rejectedTool = "forbidden_" + "x".repeat(150);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"" + rejectedTool + "\",\"arguments\":{}}}", -32602, "alice");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name=? and outcome='FAILED'", Integer.class, rejectedTool));
    }

    @Test void malformedAndInvalidRequestsReturnRequiredRpcCodes() throws Exception {
        assertCode("", -32700, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"} trailing", -32700, "alice");
        assertCode("null", -32600, "alice");
        assertCode("[]", -32600, "alice");
        assertCode("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"initialize\"}", -32600, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"initialize\"}", -32600, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"wat\"}", -32601, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}", -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":null}", -32602, "alice");
        assertCode(tool("wat", "{}"), -32602, "alice");
        assertCode(tool("get_reminder", "{\"extra\":1}"), -32602, "alice");
        assertCode(tool("get_reminder", "{}"), -32602, "alice");
        assertCode(tool("get_reminder", "{\"reminderId\":1}"), -32602, "alice");
        assertCode(tool("get_reminder", "{\"reminderId\":\"bad\"}"), -32602, "alice");
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":-1,\"idempotencyKey\":\"x\"}"), -32602, "alice");
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":1.5,\"idempotencyKey\":\"x\"}"), -32602, "alice");
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":9223372036854775808,\"idempotencyKey\":\"x\"}"), -32602, "alice");
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"idempotencyKey\":\" \"}"), -32602, "alice");
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"" + "x".repeat(201) + "\"}"), -32602, "alice");
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", -32001, null);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", -32001, " ");
        String validInitialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        assertEquals("2025-03-26", callResult(validInitialize, "alice", "bob").path("result").path("protocolVersion").asText());
        assertEquals("2025-03-26", callResult(validInitialize, "alice", " ").path("result").path("protocolVersion").asText());
        assertEquals(0, db.queryForObject("select count(*) from reminders", Integer.class));
    }

    @Test void ownershipAndOwnerlessRemindersNeverLeakAcrossUsers() throws Exception {
        var data = seed();
        String ownedBody = tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}");
        assertEquals(data.alice().id().toString(), call(ownedBody, "alice").path("result").path("structuredContent").path("id").asText());
        for (String name : new String[]{"get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status"}) {
            String args = switch (name) {
                case "get_reminder", "get_delivery_status" -> "{\"reminderId\":\"" + data.alice().id() + "\"}";
                case "update_reminder" -> "{\"reminderId\":\"" + data.alice().id() + "\",\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"deny-update\"}";
                default -> "{\"reminderId\":\"" + data.alice().id() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"deny-cancel\"}";
            };
            assertRpcError(call(tool(name, args), "bob"));
            assertRpcError(callResult(tool(name, args), "bob", "alice"));
        }
        assertEquals(0, call(tool("list_reminders", "{}"), "bob").path("result").path("structuredContent").size());
        assertEquals(0, callResult(tool("list_reminders", "{}"), "bob", "alice").path("result").path("structuredContent").size());
        String ownerless = reminders.create(data.event().id(), data.policy().id(), "rest-key").id().toString();
        assertRpcError(call(tool("get_reminder", "{\"reminderId\":\"" + ownerless + "\"}"), "alice"));
        assertEquals(1, db.queryForObject("select count(*) from reminders where owner_id is null", Integer.class));
    }

    @Test void deliveryStatusUpdateCancelAndAuditHaveRealResults() throws Exception {
        var data = seed();
        db.update("insert into notification_attempt(id,reminder_id,correlation_id,delivery_key,channel,recipient,status,provider_message_id,created_at) values(?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), data.alice().id(), UUID.randomUUID(), "delivery-1", "EMAIL", "a@example.com", "DELIVERED", "provider-7", OffsetDateTime.now());
        JsonNode status = call(tool("get_delivery_status", "{\"reminderId\":\"" + data.alice().id() + "\"}"), "alice");
        assertEquals("DELIVERED", status.path("result").path("structuredContent").get(0).path("status").asText());
        assertEquals("provider-7", status.path("result").path("structuredContent").get(0).path("providerMessageId").asText());
        JsonNode update = call(tool("update_reminder", "{\"reminderId\":\"" + data.alice().id() + "\",\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"update-1\"}"), "alice");
        assertEquals(1, update.path("result").path("structuredContent").path("version").asInt());
        assertEquals(2, db.queryForObject("select count(*) from schedule_outbox where reminder_id=? and operation='UPSERT'", Integer.class, data.alice().id()));
        JsonNode cancel = call(tool("cancel_reminder", "{\"reminderId\":\"" + data.alice().id() + "\",\"expectedVersion\":1,\"idempotencyKey\":\"cancel-1\"}"), "alice");
        assertEquals("CANCELLED", cancel.path("result").path("structuredContent").path("status").asText());
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox where reminder_id=? and operation='DELETE'", Integer.class, data.alice().id()));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='update_reminder' and user_id='alice' and outcome='SUCCEEDED' and reminder_id=?", Integer.class, data.alice().id()));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='cancel_reminder' and user_id='alice' and outcome='SUCCEEDED' and reminder_id=?", Integer.class, data.alice().id()));
        assertRpcError(call(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"), "bob"));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='get_reminder' and user_id='bob' and outcome='FAILED' and reminder_id=?", Integer.class, data.alice().id()));
    }

    @Test void auditReminderReferenceIsClearedWhenReminderIsDeleted() throws Exception {
        var data = seed();
        call(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"), "alice");
        reminders.delete(data.alice().id(), 0, "rest-delete");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where reminder_id is null and user_id='alice'", Integer.class));
    }

    @Test void sameUserRetryIsIdenticalAndDifferentUsersHaveSeparateIdempotencyScopes() throws Exception {
        var data = seed();
        String body = tool("create_reminder", "{\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"idempotencyKey\":\"same\"}");
        JsonNode first = call(body, "alice");
        JsonNode retry = call(body, "alice");
        assertEquals(first.toString(), retry.toString());
        JsonNode other = call(body, "bob");
        assertNotEquals(first.path("result").path("structuredContent").path("id").asText(), other.path("result").path("structuredContent").path("id").asText());
        assertEquals(3, db.queryForObject("select count(*) from reminders", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from mcp_audit where request_id='1'", Integer.class));
        assertEquals(2, db.queryForObject("select count(*) from mcp_audit where user_id='alice' and tool_name='create_reminder' and outcome='SUCCEEDED'", Integer.class));
    }

    private record Seed(com.middleproject.reminder.domain.Event event, com.middleproject.reminder.domain.NotificationPolicy policy, Reminder alice) {}
    private Seed seed() {
        var event = events.create("event", OffsetDateTime.now().plusHours(2), null, "seed-event-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 5, "seed-policy-" + UUID.randomUUID());
        var alice = reminders.create(event.id(), policy.id(), "seed-reminder-" + UUID.randomUUID(), "alice");
        return new Seed(event, policy, alice);
    }

    private String tool(String name, String args) { return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + args + "}}"; }
    private JsonNode call(String body, String user) throws Exception { return callResult(body, user, null); }
    private JsonNode callResult(String body, String user, String mcpUser) throws Exception {
        var request = post("/api/mcp").contentType(MediaType.APPLICATION_JSON).content(body);
        if (user != null) request.principal(() -> user);
        if (mcpUser != null) request.header("X-User-Id", mcpUser);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private void assertCode(String body, int code, String user) throws Exception { assertEquals(code, call(body, user).path("error").path("code").asInt()); }
    private void assertCodeBoth(String body, String user, String mcpUser, int code) throws Exception { assertEquals(code, callResult(body, user, mcpUser).path("error").path("code").asInt()); }
    private void assertRpcError(JsonNode node) { assertTrue(node.has("error"), node.toString()); assertTrue(node.path("error").path("code").isInt()); }
}
