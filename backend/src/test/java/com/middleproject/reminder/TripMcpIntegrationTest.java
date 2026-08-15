package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-mcp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class TripMcpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        db.update("delete from mcp_audit");
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from idempotency_record");
        db.update("delete from trip_outbox");
        db.update("delete from reminders");
        db.update("delete from notification_policies");
        db.update("delete from trip_events");
        db.update("delete from trips");
        db.update("delete from events");
    }

    private JsonNode call(String body, String user) throws Exception {
        MvcResult result = mvc.perform(post("/api/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content(body).principal(() -> user))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String tool(String name, String args) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + args + "}}";
    }

    @Test
    void tripToolsAreExposedInToolsList() throws Exception {
        JsonNode tools = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", "alice").path("result").path("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(t -> names.add(t.path("name").asText()));
        for (String expected : new String[]{"create_trip_draft", "answer_trip_question", "confirm_trip", "cancel_trip"}) {
            assertTrue(names.contains(expected), expected + " must be exposed");
            JsonNode schema = null;
            for (JsonNode t : tools) if (t.path("name").asText().equals(expected)) schema = t.path("inputSchema");
            assertEquals("object", schema.path("type").asText());
            assertTrue(!schema.path("additionalProperties").asBoolean());
        }
    }

    @Test
    void createConfirmCancelFlowWorksThroughMcp() throws Exception {
        JsonNode created = call(tool("create_trip_draft", "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\",\"idempotencyKey\":\"mcp-draft-1\"}"), "alice");
        assertEquals("DRAFT", created.path("result").path("structuredContent").path("status").asText());
        String tripId = created.path("result").path("structuredContent").path("id").asText();

        JsonNode answered = call(tool("answer_trip_question", "{\"tripId\":\"" + tripId + "\",\"question\":\"Q1\",\"answer\":\"A1\",\"idempotencyKey\":\"mcp-answer-1\"}"), "alice");
        assertEquals("A1", answered.path("result").path("structuredContent").path("draftContext").path("Q1").asText());

        JsonNode confirmed = call(tool("confirm_trip", "{\"tripId\":\"" + tripId + "\",\"confirmation\":\"mcp-confirm-1\",\"idempotencyKey\":\"mcp-confirm-1\"}"), "alice");
        assertEquals("CONFIRMED", confirmed.path("result").path("structuredContent").path("status").asText());
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, UUID.fromString(tripId)));

        long version = confirmed.path("result").path("structuredContent").path("version").asLong();
        JsonNode cancelled = call(tool("cancel_trip", "{\"tripId\":\"" + tripId + "\",\"expectedVersion\":" + version + ",\"idempotencyKey\":\"mcp-cancel-1\"}"), "alice");
        assertEquals("CANCELLED", cancelled.path("result").path("structuredContent").path("status").asText());
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=? and operation='DELETE'", Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void confirmRetryReturnsSameResultAndNoDuplicateRows() throws Exception {
        JsonNode created = call(tool("create_trip_draft", "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\",\"idempotencyKey\":\"mcp-retry-draft\"}"), "alice");
        String tripId = created.path("result").path("structuredContent").path("id").asText();
        String body = tool("confirm_trip", "{\"tripId\":\"" + tripId + "\",\"confirmation\":\"mcp-retry-confirm\",\"idempotencyKey\":\"mcp-retry-confirm\"}");
        JsonNode first = call(body, "alice");
        JsonNode replay = call(body, "alice");
        assertEquals(first.path("result").path("structuredContent").path("id").asText(), replay.path("result").path("structuredContent").path("id").asText());
        assertEquals(first.path("result").path("structuredContent").path("status").asText(), replay.path("result").path("structuredContent").path("status").asText());
        assertEquals(first.path("result").path("structuredContent").path("confirmationId").asText(), replay.path("result").path("structuredContent").path("confirmationId").asText());
        assertEquals(first.path("result").path("structuredContent").path("version").asInt(), replay.path("result").path("structuredContent").path("version").asInt());
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from notification_policies where trip_id=?", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void failedConfirmationIsReportedAndLeavesNoPartialRows() throws Exception {
        JsonNode created = call(tool("create_trip_draft", "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\",\"idempotencyKey\":\"mcp-fail-draft\"}"), "alice");
        String tripId = created.path("result").path("structuredContent").path("id").asText();
        // fresh draft is at version 0, so a confirmation would write scheduler_version 2
        db.update("insert into trip_outbox(id,trip_id,operation,expected_version,scheduler_version,due_at,payload,created_at) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), UUID.fromString(tripId), "UPSERT", 1, 2, OffsetDateTime.now(), "{}", OffsetDateTime.now());
        JsonNode bad = call(tool("confirm_trip", "{\"tripId\":\"" + tripId + "\",\"confirmation\":\"confirm\",\"idempotencyKey\":\"mcp-fail-confirm\"}"), "alice");
        assertTrue(bad.has("error"));
        assertEquals(1, db.queryForObject("select count(*) from trip_events where trip_id=? and type='DRAFT_CREATED'", Integer.class, UUID.fromString(tripId)));
        assertEquals(0, db.queryForObject("select count(*) from trip_events where trip_id=? and type='AWAITING_CONFIRMATION'", Integer.class, UUID.fromString(tripId)));
        assertEquals(0, db.queryForObject("select count(*) from trip_events where trip_id=? and type='CONFIRMED'", Integer.class, UUID.fromString(tripId)));
        assertEquals(0, db.queryForObject("select count(*) from reminders where trip_id=?", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trip_outbox where trip_id=?", Integer.class, UUID.fromString(tripId)));
        assertEquals(1, db.queryForObject("select count(*) from trips where id=?", Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void validationErrorsReturnJsonRpcErrorWithoutPersistingTrip() throws Exception {
        JsonNode blankLocation = call(tool("create_trip_draft", "{\"departure\":\" \",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-03T18:00:00+09:00\",\"idempotencyKey\":\"mcp-blank-departure\"}"), "alice");
        assertTrue(blankLocation.has("error"));
        JsonNode reversedTimes = call(tool("create_trip_draft", "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"returnAt\":\"2030-01-01T09:00:00+09:00\",\"idempotencyKey\":\"mcp-reversed-times\"}"), "alice");
        assertTrue(reversedTimes.has("error"));
        assertEquals(0, db.queryForObject("select count(*) from trips", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from trip_events", Integer.class));
    }

    @Test
    void draftCanBeCreatedWithoutReturnAt() throws Exception {
        JsonNode created = call(tool("create_trip_draft", "{\"departure\":\"Seoul\",\"destination\":\"Tokyo\",\"departureAt\":\"2030-01-01T10:00:00+09:00\",\"idempotencyKey\":\"mcp-no-return\"}"), "alice");
        assertEquals("DRAFT", created.path("result").path("structuredContent").path("status").asText());
        assertTrue(created.path("result").path("structuredContent").path("returnAt").isNull());
        assertEquals(1, db.queryForObject("select count(*) from trips", Integer.class));
    }
}
