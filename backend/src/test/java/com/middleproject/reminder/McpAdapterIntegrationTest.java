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
import java.util.HashMap;
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
        db.update("delete from device_fcm_registration");
        db.update("delete from devices");
        db.update("delete from device_pairing_codes");
        db.update("delete from mcp_audit");
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @Test void initializeAndToolsListExposeExactlySeventeenClosedSchemas() throws Exception {
        JsonNode init = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
        assertEquals("2.0", init.path("jsonrpc").asText());
        assertEquals("2025-03-26", init.path("result").path("protocolVersion").asText());

        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        assertEquals(Set.of("create_reminder", "list_reminders", "get_reminder", "update_reminder", "cancel_reminder", "get_delivery_status",
                "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
                "next_private_car_question", "preview_private_car_route", "confirm_private_car_route",
                "get_trip_travel_context", "record_trip_followup_consent", "get_trip_recommendations",
                "create_device_pairing_code"), names);
        assertEquals(17, tools.size());
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
                } else if (field.getKey().equals("accepted")) {
                    assertEquals("boolean", value.path("type").asText());
                } else if (field.getKey().equals("departureTiming")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("SAME_DAY", value.path("enum").get(0).asText());
                    assertEquals("PREVIOUS_DAY", value.path("enum").get(1).asText());
                    assertEquals(2, value.path("enum").size());
                } else if (field.getKey().equals("sort")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("DISTANCE", value.path("enum").get(0).asText());
                    assertEquals("PRICE", value.path("enum").get(1).asText());
                    assertEquals("RATING", value.path("enum").get(2).asText());
                    assertEquals(3, value.path("enum").size());
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

    @Test void everyToolExposesDescriptionsAndMcpStandardAnnotations() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        assertEquals(17, tools.size());

        // MCP-standard annotation fields with the required boolean types.
        for (JsonNode tool : tools) {
            // Phase 15 requires a useful, nonblank top-level description on every tool entry.
            assertTrue(tool.path("description").isTextual(), tool.path("name").asText() + " must expose a top-level description");
            String description = tool.path("description").asText();
            assertFalse(description.isBlank(), tool.path("name").asText() + " description must be nonblank");
            assertTrue(description.length() >= 20, tool.path("name").asText() + " description must be useful, not a stub");

            JsonNode annotations = tool.path("annotations");
            assertFalse(annotations.isMissingNode(), tool.path("name").asText() + " must expose annotations");
            assertEquals(5, annotations.size(), tool.path("name").asText() + " must expose all five annotation fields");
            assertTrue(annotations.path("title").isTextual(), tool.path("name").asText() + " title must be textual");
            assertFalse(annotations.path("title").asText().isBlank(), tool.path("name").asText() + " title must be nonblank");
            for (String field : new String[]{"readOnlyHint", "destructiveHint", "idempotentHint", "openWorldHint"}) {
                assertTrue(annotations.path(field).isBoolean(), tool.path("name").asText() + " " + field + " must be boolean");
            }
        }
    }

    @Test void createDevicePairingCodeIssuesOnceAndNeverPersistsTheRawCode() throws Exception {
        JsonNode first = call(tool("create_device_pairing_code", "{}"));
        assertFalse(first.path("result").path("isError").asBoolean());
        String code = first.path("result").path("structuredContent").path("code").asText();
        assertTrue(code.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"), "the tool must return a human-enterable pairing code");
        String text = first.path("result").path("content").get(0).path("text").asText();
        assertTrue(text.contains(code), "the model-readable text must carry the raw code so the plugin can show it");

        // The raw code never lands in any database column, idempotency record, or audit payload.
        assertEquals(0, db.queryForObject("select count(*) from device_pairing_codes where code_hash like ? or salt like ?",
                Integer.class, "%" + code + "%", "%" + code + "%"));
        assertEquals(0, db.queryForObject("select count(*) from idempotency_record where request_hash like ? or response_body like ?",
                Integer.class, "%" + code + "%", "%" + code + "%"));
        // mcp_audit has no payload column, so the code cannot leak into the audit trail by construction.
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='create_device_pairing_code' and outcome='SUCCEEDED'", Integer.class));
        assertEquals(64, db.queryForObject("select length(code_hash) from device_pairing_codes where status='ACTIVE'", Integer.class));

        // A second call while the code is active fails safely with a conflict, and the first code still works.
        JsonNode second = call(tool("create_device_pairing_code", "{}"));
        assertEquals(-32002, second.path("error").path("code").asInt(), "repeat issue must be a safe conflict");
        assertEquals(1, db.queryForObject("select count(*) from device_pairing_codes where status='ACTIVE'", Integer.class));
    }

    @Test void annotationClassificationIsTruthful() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        Map<String, JsonNode> byName = new HashMap<>();
        tools.forEach(tool -> byName.put(tool.path("name").asText(), tool));

        for (String readOnly : new String[]{"list_reminders", "get_reminder", "get_delivery_status",
                "next_private_car_question", "preview_private_car_route", "get_trip_recommendations"}) {
            assertTrue(byName.get(readOnly).path("annotations").path("readOnlyHint").asBoolean(), readOnly + " is read-only");
            assertFalse(byName.get(readOnly).path("annotations").path("destructiveHint").asBoolean(), readOnly + " is not destructive");
            // Read-only tools never mutate, so replaying them is always safe: idempotentHint must be true.
            assertTrue(byName.get(readOnly).path("annotations").path("idempotentHint").asBoolean(),
                    readOnly + " is a read-only tool and must be marked idempotent");
        }
        for (String destructive : new String[]{"cancel_reminder", "cancel_trip"}) {
            assertTrue(byName.get(destructive).path("annotations").path("destructiveHint").asBoolean(), destructive + " is destructive");
            assertFalse(byName.get(destructive).path("annotations").path("readOnlyHint").asBoolean(), destructive + " is not read-only");
        }
        // get_trip_travel_context may insert a PROPOSED consent row, so it is a write...
        assertFalse(byName.get("get_trip_travel_context").path("annotations").path("readOnlyHint").asBoolean(),
                "get_trip_travel_context may create a PROPOSED consent row and must not claim read-only");
        // ...but it is repeat-safe and must be marked idempotent.
        assertTrue(byName.get("get_trip_travel_context").path("annotations").path("idempotentHint").asBoolean(),
                "get_trip_travel_context is repeat-safe");
        for (String idempotent : new String[]{"create_reminder", "update_reminder", "cancel_reminder",
                "create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip",
                "confirm_private_car_route", "record_trip_followup_consent"}) {
            assertTrue(byName.get(idempotent).path("annotations").path("idempotentHint").asBoolean(),
                    idempotent + " is protected by an idempotencyKey and is replayable");
        }
        for (String openWorld : new String[]{"get_trip_travel_context", "get_trip_recommendations",
                "preview_private_car_route", "confirm_private_car_route"}) {
            assertTrue(byName.get(openWorld).path("annotations").path("openWorldHint").asBoolean(),
                    openWorld + " depends on external travel providers");
        }
        for (String closedWorld : new String[]{"create_reminder", "list_reminders", "get_reminder", "update_reminder",
                "cancel_reminder", "get_delivery_status", "create_trip_draft", "answer_trip_question",
                "confirm_trip", "cancel_trip", "next_private_car_question", "record_trip_followup_consent"}) {
            assertFalse(byName.get(closedWorld).path("annotations").path("openWorldHint").asBoolean(),
                    closedWorld + " does not depend on travel providers");
        }
    }

    @Test void toolResultsCarryTextContentAndStructuredContent() throws Exception {
        var data = seed();
        JsonNode response = call(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"));
        JsonNode result = response.path("result");
        assertFalse(result.path("isError").asBoolean());
        JsonNode content = result.path("content");
        assertTrue(content.isArray() && content.size() >= 1);
        assertEquals("text", content.get(0).path("type").asText());
        assertFalse(content.get(0).path("text").asText().isBlank(), "text content must be model-readable");
        JsonNode structured = result.path("structuredContent");
        assertEquals(data.alice().id().toString(), structured.path("id").asText());
        assertEquals(content.get(0).path("text").asText(), structured.toString());
    }

    @Test void initializeNegotiatesVersionAndRejectsMalformedParameters() throws Exception {
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", -32602);
        assertEquals("2025-03-26", call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}").path("result").path("protocolVersion").asText());
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":[],\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", -32602);
        assertEquals("2025-03-26", call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}").path("result").path("protocolVersion").asText());
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":[]}}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"\",\"version\":\"1\"}}}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\" \"}}}", -32602);
    }

    @Test void initializedNotificationIsAcceptedWithoutAJsonRpcResponse() throws Exception {
        mvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isNoContent());
    }

    @Test void unexpectedToolFailureReturnsSanitizedJsonRpcError() throws Exception {
        doThrow(new IllegalStateException("sentinel-unexpected-application-failure"))
                .when(queries).deliveryStatus(org.mockito.ArgumentMatchers.any());
        JsonNode response = call(tool("get_delivery_status", "{\"reminderId\":\"" + seed().alice().id() + "\"}"));
        assertEquals(-32000, response.path("error").path("code").asInt());
        assertEquals("Tool execution failed", response.path("error").path("message").asText());
        assertFalse(response.toString().contains("sentinel-unexpected-application-failure"));
    }

    @Test void longJsonRpcIdentifiersAndRejectedToolNamesRemainAudited() throws Exception {
        String requestId = "r".repeat(500);
        call("{\"jsonrpc\":\"2.0\",\"id\":\"" + requestId + "\",\"method\":\"tools/list\"}");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where request_id=?", Integer.class, requestId));

        String rejectedTool = "forbidden_" + "x".repeat(150);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"" + rejectedTool + "\",\"arguments\":{}}}", -32602);
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name=? and outcome='FAILED'", Integer.class, rejectedTool));
    }

    @Test void malformedAndInvalidRequestsReturnRequiredRpcCodes() throws Exception {
        assertCode("", -32700);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"} trailing", -32700);
        assertCode("null", -32600);
        assertCode("[]", -32600);
        assertCode("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"initialize\"}", -32600);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"initialize\"}", -32600);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"wat\"}", -32601);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}", -32602);
        assertCode("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":null}", -32602);
        assertCode(tool("wat", "{}"), -32602);
        assertCode(tool("get_reminder", "{\"extra\":1}"), -32602);
        assertCode(tool("get_reminder", "{}"), -32602);
        assertCode(tool("get_reminder", "{\"reminderId\":1}"), -32602);
        assertCode(tool("get_reminder", "{\"reminderId\":\"bad\"}"), -32602);
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":-1,\"idempotencyKey\":\"x\"}"), -32602);
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":1.5,\"idempotencyKey\":\"x\"}"), -32602);
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":9223372036854775808,\"idempotencyKey\":\"x\"}"), -32602);
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"idempotencyKey\":\" \"}"), -32602);
        assertCode(tool("cancel_reminder", "{\"reminderId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"" + "x".repeat(201) + "\"}"), -32602);
        String validInitialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        assertEquals("2025-03-26", callResult(validInitialize, null, "mallory").path("result").path("protocolVersion").asText());
        assertEquals("2025-03-26", callResult(validInitialize, "bob", "alice").path("result").path("protocolVersion").asText());
        assertEquals(0, db.queryForObject("select count(*) from reminders", Integer.class));
    }

    @Test void nullPrincipalAndArbitraryIdentityHeadersAllResolveToDemoOwner() throws Exception {
        var data = seed();
        // A request with no Principal and a hostile X-User-Id must run as the demo owner.
        JsonNode ownerless = callResult(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"), null, "mallory");
        assertEquals(data.alice().id().toString(), ownerless.path("result").path("structuredContent").path("id").asText());
        // A Principal named mallory with no header is still the demo owner.
        JsonNode princ = callResult(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"), "mallory", null);
        assertEquals(data.alice().id().toString(), princ.path("result").path("structuredContent").path("id").asText());
        assertEquals(0, db.queryForObject("select count(*) from mcp_audit where user_id='mallory'", Integer.class));
        assertEquals(2, db.queryForObject("select count(*) from mcp_audit where user_id='demo-owner' and tool_name='get_reminder' and outcome='SUCCEEDED'", Integer.class));
    }

    @Test void ownerlessRemindersNeverLeakIntoTheSingleOwnerScope() throws Exception {
        var data = seed();
        String ownerless = reminders.create(data.event().id(), data.policy().id(), "rest-key").id().toString();
        assertRpcError(call(tool("get_reminder", "{\"reminderId\":\"" + ownerless + "\"}")));
        assertEquals(1, db.queryForObject("select count(*) from reminders where owner_id is null", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where user_id='demo-owner' and tool_name='get_reminder' and outcome='FAILED' and reminder_id=?", Integer.class, UUID.fromString(ownerless)));
    }

    @Test void deliveryStatusUpdateCancelAndAuditHaveRealResults() throws Exception {
        var data = seed();
        db.update("insert into notification_attempt(id,reminder_id,correlation_id,delivery_key,channel,recipient,status,provider_message_id,created_at) values(?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), data.alice().id(), UUID.randomUUID(), "delivery-1", "EMAIL", "a@example.com", "DELIVERED", "provider-7", OffsetDateTime.now());
        JsonNode status = call(tool("get_delivery_status", "{\"reminderId\":\"" + data.alice().id() + "\"}"));
        assertEquals("DELIVERED", status.path("result").path("structuredContent").get(0).path("status").asText());
        assertEquals("provider-7", status.path("result").path("structuredContent").get(0).path("providerMessageId").asText());
        JsonNode update = call(tool("update_reminder", "{\"reminderId\":\"" + data.alice().id() + "\",\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"expectedVersion\":0,\"idempotencyKey\":\"update-1\"}"));
        assertEquals(1, update.path("result").path("structuredContent").path("version").asInt());
        assertEquals(2, db.queryForObject("select count(*) from schedule_outbox where reminder_id=? and operation='UPSERT'", Integer.class, data.alice().id()));
        JsonNode cancel = call(tool("cancel_reminder", "{\"reminderId\":\"" + data.alice().id() + "\",\"expectedVersion\":1,\"idempotencyKey\":\"cancel-1\"}"));
        assertEquals("CANCELLED", cancel.path("result").path("structuredContent").path("status").asText());
        assertEquals(1, db.queryForObject("select count(*) from schedule_outbox where reminder_id=? and operation='DELETE'", Integer.class, data.alice().id()));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='update_reminder' and user_id='demo-owner' and outcome='SUCCEEDED' and reminder_id=?", Integer.class, data.alice().id()));
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where tool_name='cancel_reminder' and user_id='demo-owner' and outcome='SUCCEEDED' and reminder_id=?", Integer.class, data.alice().id()));
    }

    @Test void auditReminderReferenceIsClearedWhenReminderIsDeleted() throws Exception {
        var data = seed();
        call(tool("get_reminder", "{\"reminderId\":\"" + data.alice().id() + "\"}"));
        reminders.delete(data.alice().id(), 0, "rest-delete");
        assertEquals(1, db.queryForObject("select count(*) from mcp_audit where reminder_id is null and user_id='demo-owner'", Integer.class));
    }

    @Test void sameKeyRetryIsIdenticalAndAuditStaysOnDemoOwner() throws Exception {
        var data = seed();
        String body = tool("create_reminder", "{\"eventId\":\"" + data.event().id() + "\",\"policyId\":\"" + data.policy().id() + "\",\"idempotencyKey\":\"same\"}");
        JsonNode first = call(body);
        JsonNode retry = call(body);
        assertEquals(first.toString(), retry.toString());
        // A hostile X-User-Id cannot change the idempotency scope: the retry with the same
        // key is still resolved against the demo owner's scope.
        JsonNode hostile = callResult(body, null, "mallory");
        assertEquals(first.path("result").path("structuredContent").path("id").asText(), hostile.path("result").path("structuredContent").path("id").asText());
        // seed() created one reminder; the three identical requests must create exactly one
        // more (delta one), proving the retries were resolved as duplicates rather than
        // creating new reminders.
        assertEquals(2, db.queryForObject("select count(*) from reminders", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from reminders where id=?", Integer.class, UUID.fromString(first.path("result").path("structuredContent").path("id").asText())));
        assertEquals(1, db.queryForObject("select count(*) from reminders where id=?", Integer.class, data.alice().id()));
        // Each of the three identical requests was audited; all three resolved against the
        // demo owner's idempotency scope (one COMPLETED record, no duplicate reminder).
        assertEquals(3, db.queryForObject("select count(*) from mcp_audit where request_id='1'", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from mcp_audit where user_id='demo-owner' and tool_name='create_reminder' and outcome='SUCCEEDED'", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from mcp_audit where user_id='mallory'", Integer.class));
        // The hostile retry resolved against the demo owner's scope: exactly one idempotency
        // record for the shared key (COMPLETED), and no duplicate reminder was created.
        assertEquals(1, db.queryForObject("select count(*) from idempotency_record where idempotency_key='same' and scope like 'reminders:create:%'", Integer.class));
        assertEquals("COMPLETED", db.queryForObject("select status from idempotency_record where idempotency_key='same' and scope like 'reminders:create:%'", String.class));
    }

    private record Seed(com.middleproject.reminder.domain.Event event, com.middleproject.reminder.domain.NotificationPolicy policy, Reminder alice) {}
    private Seed seed() {
        var event = events.create("event", OffsetDateTime.now().plusHours(2), null, "seed-event-" + UUID.randomUUID());
        var policy = policies.create("EMAIL", 5, "seed-policy-" + UUID.randomUUID());
        var alice = reminders.create(event.id(), policy.id(), "seed-reminder-" + UUID.randomUUID(), "demo-owner");
        return new Seed(event, policy, alice);
    }

    private String tool(String name, String args) { return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + args + "}}"; }
    private JsonNode call(String body) throws Exception { return callResult(body, null, null); }
    private JsonNode callResult(String body, String user, String mcpUser) throws Exception {
        var request = post("/api/mcp").contentType(MediaType.APPLICATION_JSON).content(body);
        if (user != null) request.principal(() -> user);
        if (mcpUser != null) request.header("X-User-Id", mcpUser);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private void assertCode(String body, int code) throws Exception { assertEquals(code, call(body).path("error").path("code").asInt()); }
    private void assertRpcError(JsonNode node) { assertTrue(node.has("error"), node.toString()); assertTrue(node.path("error").path("code").isInt()); }
}
