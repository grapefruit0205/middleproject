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
        db.update("delete from transit_favorites");
        db.update("delete from origin_favorites");
        db.update("delete from mcp_audit");
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @Test void initializeAndToolsListExposeTripMobilityAndOriginFavoritesWithClosedSchemas() throws Exception {
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
                "create_device_pairing_code", "search_subway_stations", "get_realtime_subway_arrivals",
                "get_subway_station_schedule", "find_nearby_bus_stops", "find_bus_stops_by_landmark", "get_bus_arrivals", "search_bus_routes",
                "get_express_bus_arrivals", "get_intercity_bus_schedule",
                "save_subway_favorite", "save_bus_favorite", "list_transit_favorites",
                "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route",
                "save_origin_favorite", "list_origin_favorites", "delete_origin_favorite"), names);
        assertEquals(35, tools.size());
        for (JsonNode tool : tools) {
            JsonNode schema = tool.path("inputSchema");
            assertEquals("object", schema.path("type").asText());
            assertFalse(schema.path("additionalProperties").asBoolean());
            if (tool.path("name").asText().equals("create_trip_draft")) {
                // returnAt is optional for a draft because the return time may not be known yet
                assertEquals(schema.path("properties").size() - 1, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("returnAt"));
            } else if (tool.path("name").asText().equals("get_realtime_subway_arrivals")) {
                // limit is optional with default 20
                assertEquals(schema.path("properties").size() - 1, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("limit"));
                assertTrue(schema.path("required").toString().contains("stationName"));
            } else if (tool.path("name").asText().equals("get_subway_station_schedule")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("subwayStationId"));
                assertTrue(schema.path("required").toString().contains("dailyTypeCode"));
                assertTrue(schema.path("required").toString().contains("upDownTypeCode"));
            } else if (tool.path("name").asText().equals("find_nearby_bus_stops")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("gpsLati"));
                assertTrue(schema.path("required").toString().contains("gpsLong"));
            } else if (tool.path("name").asText().equals("get_bus_arrivals")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("cityCode"));
                assertTrue(schema.path("required").toString().contains("nodeId"));
            } else if (tool.path("name").asText().equals("search_bus_routes")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("cityCode"));
                assertTrue(schema.path("required").toString().contains("routeNo"));
            } else if (tool.path("name").asText().equals("get_express_bus_arrivals")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("depTerminalCode"));
                assertTrue(schema.path("required").toString().contains("arrTerminalCode"));
            } else if (tool.path("name").asText().equals("get_intercity_bus_schedule")) {
                // pageNo and numOfRows are optional
                assertEquals(schema.path("properties").size() - 2, schema.path("required").size());
                assertFalse(schema.path("required").toString().contains("pageNo"));
                assertFalse(schema.path("required").toString().contains("numOfRows"));
                assertTrue(schema.path("required").toString().contains("depTerminalId"));
                assertTrue(schema.path("required").toString().contains("arrTerminalId"));
                assertTrue(schema.path("required").toString().contains("depPlandTime"));
            } else if (tool.path("name").asText().equals("resolve_place")) {
                assertEquals(schema.path("properties").size() - 1, schema.path("required").size());
                assertTrue(schema.path("required").toString().contains("query"));
                assertFalse(schema.path("required").toString().contains("limit"));
            } else if (tool.path("name").asText().equals("find_nearby_subway_stations")) {
                assertEquals(schema.path("properties").size() - 1, schema.path("required").size());
                assertTrue(schema.path("required").toString().contains("latitude"));
                assertTrue(schema.path("required").toString().contains("longitude"));
                assertFalse(schema.path("required").toString().contains("radiusMeters"));
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
                } else if (field.getKey().equals("pageNo")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(1, value.path("minimum").asInt());
                } else if (field.getKey().equals("numOfRows")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(1, value.path("minimum").asInt());
                    if (tool.path("name").asText().equals("search_subway_stations")
                            || tool.path("name").asText().equals("find_nearby_bus_stops")
                            || tool.path("name").asText().equals("get_bus_arrivals")
                            || tool.path("name").asText().equals("search_bus_routes")) {
                        assertEquals(50, value.path("maximum").asInt());
                    } else {
                        assertEquals(100, value.path("maximum").asInt());
                    }
                } else if (field.getKey().equals("limit")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(1, value.path("minimum").asInt());
                    assertEquals(tool.path("name").asText().equals("resolve_place") ? 15 : 100, value.path("maximum").asInt());
                } else if (field.getKey().equals("cityCode")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(1, value.path("minimum").asInt());
                } else if (field.getKey().equals("gpsLati")) {
                    assertEquals("number", value.path("type").asText());
                    assertEquals(33.0, value.path("minimum").asDouble());
                    assertEquals(39.0, value.path("maximum").asDouble());
                } else if (field.getKey().equals("gpsLong")) {
                    assertEquals("number", value.path("type").asText());
                    assertEquals(124.0, value.path("minimum").asDouble());
                    assertEquals(132.0, value.path("maximum").asDouble());
                } else if (field.getKey().equals("latitude")) {
                    assertEquals("number", value.path("type").asText());
                    assertEquals(33.0, value.path("minimum").asDouble());
                    assertEquals(39.0, value.path("maximum").asDouble());
                } else if (field.getKey().equals("longitude")) {
                    assertEquals("number", value.path("type").asText());
                    assertEquals(124.0, value.path("minimum").asDouble());
                    assertEquals(132.0, value.path("maximum").asDouble());
                } else if (field.getKey().equals("radiusMeters")) {
                    assertEquals("integer", value.path("type").asText());
                    assertEquals(1, value.path("minimum").asInt());
                    assertEquals(20_000, value.path("maximum").asInt());
                } else if (field.getKey().equals("depPlandTime")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("^[0-9]{8}$", value.path("pattern").asText());
                    assertEquals(8, value.path("minLength").asInt());
                    assertEquals(8, value.path("maxLength").asInt());
                } else if (field.getKey().equals("dailyTypeCode")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("01", value.path("enum").get(0).asText());
                    assertEquals("02", value.path("enum").get(1).asText());
                    assertEquals("03", value.path("enum").get(2).asText());
                    assertEquals(3, value.path("enum").size());
                } else if (field.getKey().equals("upDownTypeCode")) {
                    assertEquals("string", value.path("type").asText());
                    assertEquals("U", value.path("enum").get(0).asText());
                    assertEquals("D", value.path("enum").get(1).asText());
                    assertEquals(2, value.path("enum").size());
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
                            && !field.getKey().equals("confirmationId")
                            && !field.getKey().equals("subwayStationId")
                            && !field.getKey().equals("nodeId")
                            && !field.getKey().equals("depTerminalId")
                            && !field.getKey().equals("arrTerminalId")) {
                        assertEquals("uuid", value.path("format").asText());
                    }
                    if (field.getKey().equals("subwayStationId")
                            || field.getKey().equals("nodeId")
                            || field.getKey().equals("depTerminalId")
                            || field.getKey().equals("arrTerminalId")) {
                        assertFalse(value.has("format"), field.getKey() + " must not be marked as a UUID");
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
        Map<String, JsonNode> byName = new HashMap<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        tools.forEach(tool -> byName.put(tool.path("name").asText(), tool));
        assertEquals(35, tools.size());

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

        String createTripDescription = byName.get("create_trip_draft").path("description").asText();
        assertTrue(createTripDescription.contains("Do not call this tool until the user has provided a departure location"));
        assertTrue(createTripDescription.contains("ask where they will depart from"));

        String subwayArrivalDescription = byName.get("get_realtime_subway_arrivals").path("description").asText();
        assertTrue(subwayArrivalDescription.contains("If the user asks a vague question such as subway when does it arrive"));
        assertTrue(subwayArrivalDescription.contains("ask for their departure station before calling this tool"));

        String nearbyBusDescription = byName.get("find_nearby_bus_stops").path("description").asText();
        assertTrue(nearbyBusDescription.contains("Use its returned cityCode and nodeId as inputs to get_bus_arrivals"));
        assertTrue(nearbyBusDescription.contains("If the user asks a vague question such as bus when does it arrive"));
        assertTrue(nearbyBusDescription.contains("ask where they are departing from"));
        String busArrivalDescription = byName.get("get_bus_arrivals").path("description").asText();
        assertTrue(busArrivalDescription.contains("Do not ask the user to supply these internal identifiers"));
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
                "next_private_car_question", "preview_private_car_route", "get_trip_recommendations",
                "search_subway_stations", "get_realtime_subway_arrivals", "get_subway_station_schedule",
                "find_nearby_bus_stops", "find_bus_stops_by_landmark", "get_bus_arrivals", "search_bus_routes",
                "get_express_bus_arrivals", "get_intercity_bus_schedule", "list_transit_favorites",
                "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route", "list_origin_favorites"}) {
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
                "confirm_private_car_route", "record_trip_followup_consent", "save_subway_favorite", "save_bus_favorite",
                "save_origin_favorite", "delete_origin_favorite"}) {
            assertTrue(byName.get(idempotent).path("annotations").path("idempotentHint").asBoolean(),
                    idempotent + " is protected by an idempotencyKey and is replayable");
        }
        for (String openWorld : new String[]{"get_trip_travel_context", "get_trip_recommendations",
                "preview_private_car_route", "confirm_private_car_route", "search_subway_stations",
                "get_realtime_subway_arrivals", "get_subway_station_schedule", "find_nearby_bus_stops", "find_bus_stops_by_landmark",
                "get_bus_arrivals", "search_bus_routes", "get_express_bus_arrivals", "get_intercity_bus_schedule",
                "resolve_place", "find_nearby_subway_stations", "preview_public_transit_route"}) {
            assertTrue(byName.get(openWorld).path("annotations").path("openWorldHint").asBoolean(),
                    openWorld + " depends on external travel providers");
        }
        for (String closedWorld : new String[]{"create_reminder", "list_reminders", "get_reminder", "update_reminder",
                "cancel_reminder", "get_delivery_status", "create_trip_draft", "answer_trip_question",
                "confirm_trip", "cancel_trip", "next_private_car_question", "record_trip_followup_consent",
                "save_subway_favorite", "save_bus_favorite", "list_transit_favorites",
                "save_origin_favorite", "list_origin_favorites", "delete_origin_favorite"}) {
            assertFalse(byName.get(closedWorld).path("annotations").path("openWorldHint").asBoolean(),
                    closedWorld + " does not depend on travel providers");
        }
    }

    @Test void searchSubwayStationsToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("search_subway_stations".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "search_subway_stations tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(3, schema.path("required").size());

        // Invalid calls rejected with -32602
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"\",\"pageNo\":1,\"numOfRows\":10}"), -32602);
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"   \",\"pageNo\":1,\"numOfRows\":10}"), -32602);
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"Gangnam\",\"pageNo\":0,\"numOfRows\":10}"), -32602);
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"Gangnam\",\"pageNo\":1,\"numOfRows\":0}"), -32602);
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"Gangnam\",\"pageNo\":1,\"numOfRows\":51}"), -32602);
        assertCode(tool("search_subway_stations", "{\"subwayStationName\":\"Gangnam\",\"pageNo\":1,\"numOfRows\":10,\"extra\":true}"), -32602);

        // Valid call dispatches and returns structured transport outcome
        JsonNode valid = call(tool("search_subway_stations", "{\"subwayStationName\":\"Gangnam\",\"pageNo\":1,\"numOfRows\":10}"));
        assertFalse(valid.path("result").path("isError").asBoolean());
        assertNotNull(valid.path("result").path("structuredContent"));
        assertTrue(valid.path("result").path("structuredContent").has("success") || valid.path("result").path("structuredContent").has("failureKind") || valid.path("result").path("structuredContent").has("value") || valid.path("result").path("structuredContent").has("empty"));
    }

    @Test void tripMobilityToolsResolvePlacesPreviewRoutesAndKeepOriginFavoritesOwnerScoped() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        Map<String, JsonNode> byName = new HashMap<>();
        tools.forEach(tool -> byName.put(tool.path("name").asText(), tool));

        JsonNode resolve = byName.get("resolve_place");
        assertNotNull(resolve);
        assertTrue(resolve.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(resolve.path("annotations").path("openWorldHint").asBoolean());
        assertTrue(resolve.path("inputSchema").path("required").toString().contains("query"));
        assertFalse(resolve.path("inputSchema").path("required").toString().contains("limit"));
        assertCode(tool("resolve_place", "{}"), -32602);
        assertCode(tool("resolve_place", "{\"query\":\"\"}"), -32602);

        JsonNode subway = byName.get("find_nearby_subway_stations");
        assertNotNull(subway);
        assertTrue(subway.path("annotations").path("readOnlyHint").asBoolean());
        assertCode(tool("find_nearby_subway_stations", "{\"latitude\":37.5}"), -32602);
        assertCode(tool("find_nearby_subway_stations", "{\"latitude\":50,\"longitude\":127}"), -32602);

        JsonNode route = byName.get("preview_public_transit_route");
        assertNotNull(route);
        assertTrue(route.path("annotations").path("readOnlyHint").asBoolean());
        assertCode(tool("preview_public_transit_route", "{\"origin\":\"\" ,\"destination\":\"부산역\"}"), -32602);

        JsonNode saved = call(tool("save_origin_favorite", "{\"alias\":\"회사\",\"placeName\":\"강남역\",\"address\":\"서울 강남구\",\"latitude\":37.4979,\"longitude\":127.0276,\"idempotencyKey\":\"origin-1\"}"));
        String favoriteId = saved.path("result").path("structuredContent").path("id").asText();
        assertFalse(favoriteId.isBlank(), saved.toString());
        assertEquals("회사", saved.path("result").path("structuredContent").path("alias").asText());
        assertEquals(1, call(tool("list_origin_favorites", "{}")).path("result").path("structuredContent").size());

        JsonNode deleted = call(tool("delete_origin_favorite", "{\"originFavoriteId\":\"" + favoriteId + "\",\"idempotencyKey\":\"origin-delete-1\"}"));
        assertEquals(favoriteId, deleted.path("result").path("structuredContent").path("id").asText());
        assertEquals(0, call(tool("list_origin_favorites", "{}")).path("result").path("structuredContent").size());
    }

    @Test void transitFavoriteWriteIsIdempotentOwnerScopedAndVisibleToWidgetRead() throws Exception {
        JsonNode saved = call(tool("save_subway_favorite", """
                {"alias":"회사역","stationName":"강남","idempotencyKey":"fav-1"}
                """));
        assertEquals("회사역", saved.path("result").path("structuredContent").path("alias").asText());

        JsonNode replay = call(tool("save_subway_favorite", """
                {"alias":"회사역","stationName":"강남","idempotencyKey":"fav-1"}
                """));
        assertEquals(saved.path("result").path("structuredContent"), replay.path("result").path("structuredContent"));

        JsonNode listed = call(tool("list_transit_favorites", "{}"));
        assertEquals(1, listed.path("result").path("structuredContent").size());
        assertEquals("SUBWAY", listed.path("result").path("structuredContent").get(0).path("mode").asText());
    }

    @Test void getRealtimeSubwayArrivalsToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("get_realtime_subway_arrivals".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "get_realtime_subway_arrivals tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(1, schema.path("required").size());
        assertEquals("stationName", schema.path("required").get(0).asText());

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("get_realtime_subway_arrivals", "{}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"\"}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"   \"}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":123}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":0}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":101}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":1.5}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":\"20\"}"), -32602);
        assertCode(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":20,\"extra\":true}"), -32602);

        // Valid call with default limit dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        JsonNode disabled = defaultValid.path("result").path("structuredContent");
        assertFalse(disabled.path("success").asBoolean());
        assertFalse(disabled.path("empty").asBoolean());
        assertEquals("DISABLED_INSECURE", disabled.path("failureKind").asText());
        assertTrue(disabled.path("errorMessage").asText().contains("disabled"));

        // Valid call with explicit limit dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("get_realtime_subway_arrivals", "{\"stationName\":\"Gangnam\",\"limit\":5}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void getSubwayStationScheduleToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("get_subway_station_schedule".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "get_subway_station_schedule tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(3, schema.path("required").size());

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("get_subway_station_schedule", "{}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"   \",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"04\",\"upDownTypeCode\":\"U\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"1\",\"upDownTypeCode\":\"U\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"X\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"u\"}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\",\"pageNo\":0}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\",\"numOfRows\":0}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\",\"numOfRows\":101}"), -32602);
        assertCode(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\",\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"01\",\"upDownTypeCode\":\"U\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("get_subway_station_schedule", "{\"subwayStationId\":\"SUB123\",\"dailyTypeCode\":\"02\",\"upDownTypeCode\":\"D\",\"pageNo\":2,\"numOfRows\":50}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void findNearbyBusStopsToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("find_nearby_bus_stops".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "find_nearby_bus_stops tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(2, schema.path("required").size());
        assertTrue(schema.path("required").toString().contains("gpsLati"));
        assertTrue(schema.path("required").toString().contains("gpsLong"));

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("find_nearby_bus_stops", "{}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLong\":127.0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":\"37.5\",\"gpsLong\":127.0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":\"127.0\"}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":true,\"gpsLong\":127.0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":32.9,\"gpsLong\":127.0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":39.1,\"gpsLong\":127.0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":123.9}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":132.1}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":127.0,\"pageNo\":0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":127.0,\"numOfRows\":0}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":127.0,\"numOfRows\":51}"), -32602);
        assertCode(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5,\"gpsLong\":127.0,\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("find_nearby_bus_stops", "{\"gpsLati\":37.5665,\"gpsLong\":126.9780}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with integer coordinates and explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("find_nearby_bus_stops", "{\"gpsLati\":37,\"gpsLong\":127,\"pageNo\":2,\"numOfRows\":10}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void getBusArrivalsToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("get_bus_arrivals".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "get_bus_arrivals tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(2, schema.path("required").size());
        assertTrue(schema.path("required").toString().contains("cityCode"));
        assertTrue(schema.path("required").toString().contains("nodeId"));

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("get_bus_arrivals", "{}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"nodeId\":\"DJB8001793\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"   \"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":123}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":0,\"nodeId\":\"DJB8001793\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":-1,\"nodeId\":\"DJB8001793\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":\"25\",\"nodeId\":\"DJB8001793\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25.5,\"nodeId\":\"DJB8001793\"}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\",\"pageNo\":0}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\",\"numOfRows\":0}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\",\"numOfRows\":51}"), -32602);
        assertCode(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\",\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("get_bus_arrivals", "{\"cityCode\":25,\"nodeId\":\"DJB8001793\",\"pageNo\":2,\"numOfRows\":10}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void searchBusRoutesToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("search_bus_routes".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "search_bus_routes tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(2, schema.path("required").size());
        assertTrue(schema.path("required").toString().contains("cityCode"));
        assertTrue(schema.path("required").toString().contains("routeNo"));

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("search_bus_routes", "{}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25}"), -32602);
        assertCode(tool("search_bus_routes", "{\"routeNo\":\"101\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"   \"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":101}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":0,\"routeNo\":\"101\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":-5,\"routeNo\":\"101\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":\"25\",\"routeNo\":\"101\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25.5,\"routeNo\":\"101\"}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\",\"pageNo\":0}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\",\"numOfRows\":0}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\",\"numOfRows\":51}"), -32602);
        assertCode(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\",\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("search_bus_routes", "{\"cityCode\":25,\"routeNo\":\"101\",\"pageNo\":2,\"numOfRows\":10}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void getExpressBusArrivalsToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("get_express_bus_arrivals".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "get_express_bus_arrivals tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(2, schema.path("required").size());
        assertTrue(schema.path("required").toString().contains("depTerminalCode"));
        assertTrue(schema.path("required").toString().contains("arrTerminalCode"));

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("get_express_bus_arrivals", "{}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"arrTerminalCode\":\"NAEK020\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"\",\"arrTerminalCode\":\"NAEK020\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"   \",\"arrTerminalCode\":\"NAEK020\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"   \"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":10,\"arrTerminalCode\":\"NAEK020\"}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":20}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\",\"pageNo\":0}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\",\"numOfRows\":0}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\",\"numOfRows\":101}"), -32602);
        assertCode(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\",\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("get_express_bus_arrivals", "{\"depTerminalCode\":\"NAEK010\",\"arrTerminalCode\":\"NAEK020\",\"pageNo\":2,\"numOfRows\":50}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
    }

    @Test void getIntercityBusScheduleToolIsListedReadOnlyClosedSchemaAndDispatches() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").path("result").path("tools");
        JsonNode tool = null;
        for (JsonNode t : tools) {
            if ("get_intercity_bus_schedule".equals(t.path("name").asText())) {
                tool = t;
                break;
            }
        }
        assertNotNull(tool, "get_intercity_bus_schedule tool must be listed");
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean());
        assertFalse(tool.path("annotations").path("destructiveHint").asBoolean());
        assertTrue(tool.path("annotations").path("openWorldHint").asBoolean());
        JsonNode schema = tool.path("inputSchema");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("object", schema.path("type").asText());
        assertEquals(3, schema.path("required").size());
        assertTrue(schema.path("required").toString().contains("depTerminalId"));
        assertTrue(schema.path("required").toString().contains("arrTerminalId"));
        assertTrue(schema.path("required").toString().contains("depPlandTime"));

        // Invalid calls rejected with -32602 before service call
        assertCode(tool("get_intercity_bus_schedule", "{}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"   \",\"depPlandTime\":\"20260817\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":10,\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"2026-08-17\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"2026081\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"bad\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260230\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20261301\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260001\"}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\",\"pageNo\":0}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\",\"numOfRows\":0}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\",\"numOfRows\":101}"), -32602);
        assertCode(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\",\"extra\":true}"), -32602);

        // Valid call with defaults dispatches and returns structured transport outcome
        JsonNode defaultValid = call(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\"}"));
        assertFalse(defaultValid.path("result").path("isError").asBoolean());
        assertNotNull(defaultValid.path("result").path("structuredContent"));
        assertTrue(defaultValid.path("result").path("structuredContent").has("success") || defaultValid.path("result").path("structuredContent").has("failureKind") || defaultValid.path("result").path("structuredContent").has("value") || defaultValid.path("result").path("structuredContent").has("empty"));

        // Valid call with explicit pagination dispatches and returns structured transport outcome
        JsonNode explicitValid = call(tool("get_intercity_bus_schedule", "{\"depTerminalId\":\"NAEK010\",\"arrTerminalId\":\"NAEK020\",\"depPlandTime\":\"20260817\",\"pageNo\":2,\"numOfRows\":50}"));
        assertFalse(explicitValid.path("result").path("isError").asBoolean());
        assertNotNull(explicitValid.path("result").path("structuredContent"));
        assertTrue(explicitValid.path("result").path("structuredContent").has("success") || explicitValid.path("result").path("structuredContent").has("failureKind") || explicitValid.path("result").path("structuredContent").has("value") || explicitValid.path("result").path("structuredContent").has("empty"));
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
