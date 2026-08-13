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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crud-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc
class CrudApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private record HttpResult(int status, String body) { }

    @BeforeEach void reset() {
        db.update("delete from idempotency_record");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
    }

    @Test void eventCrudAndOptimisticLocking() throws Exception {
        String created = mvc.perform(post("/api/events").header("Idempotency-Key", "event-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"meeting\",\"startsAt\":\"2030-01-01T10:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", notNullValue())).andExpect(jsonPath("$.version").value(0)).andReturn().getResponse().getContentAsString();
        String id = node(created, "id");
        mvc.perform(get("/api/events")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/events/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("meeting"));
        String updated = mvc.perform(put("/api/events/" + id).header("Idempotency-Key", "event-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"updated\",\"startsAt\":\"2030-01-01T11:00:00Z\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
        String replayedUpdate = mvc.perform(put("/api/events/" + id).header("Idempotency-Key", "event-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"updated\",\"startsAt\":\"2030-01-01T11:00:00Z\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(updated, replayedUpdate);
        org.junit.jupiter.api.Assertions.assertEquals(1, db.queryForObject("select version from events where id=?", Long.class, UUID.fromString(id)));
        mvc.perform(put("/api/events/" + id).header("Idempotency-Key", "event-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stale\",\"startsAt\":\"2030-01-01T10:00:00Z\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/events/" + id).header("Idempotency-Key", "event-delete-stale").param("expectedVersion", "0")).andExpect(status().isConflict());
        var deletedEvent = mvc.perform(delete("/api/events/" + id).header("Idempotency-Key", "event-delete-1").param("expectedVersion", "1")).andExpect(status().isNoContent()).andReturn().getResponse();
        var replayedDeletedEvent = mvc.perform(delete("/api/events/" + id).header("Idempotency-Key", "event-delete-1").param("expectedVersion", "1")).andExpect(status().isNoContent()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(deletedEvent.getStatus(), replayedDeletedEvent.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(deletedEvent.getContentAsString(), replayedDeletedEvent.getContentAsString());
        mvc.perform(get("/api/events/" + id)).andExpect(status().isNotFound());
    }

    @Test void policyCrudAndRetry() throws Exception {
        String body = "{\"channel\":\"EMAIL\",\"leadMinutes\":10}";
        String first = mvc.perform(post("/api/notification-policies").header("Idempotency-Key", "policy-1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = node(first, "id");
        String replayedCreate = mvc.perform(post("/api/notification-policies").header("Idempotency-Key", "policy-1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(first, replayedCreate);
        mvc.perform(post("/api/notification-policies").header("Idempotency-Key", "policy-1").contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\",\"leadMinutes\":99}")).andExpect(status().isConflict());
        mvc.perform(get("/api/notification-policies")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        String updated = mvc.perform(put("/api/notification-policies/" + id).header("Idempotency-Key", "policy-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\",\"leadMinutes\":20,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
        String replayedUpdate = mvc.perform(put("/api/notification-policies/" + id).header("Idempotency-Key", "policy-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\",\"leadMinutes\":20,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(updated, replayedUpdate);
        var deletedPolicy = mvc.perform(delete("/api/notification-policies/" + id).header("Idempotency-Key", "policy-delete-1").param("expectedVersion", "1")).andExpect(status().isNoContent()).andReturn().getResponse();
        var replayedDeletedPolicy = mvc.perform(delete("/api/notification-policies/" + id).header("Idempotency-Key", "policy-delete-1").param("expectedVersion", "1")).andExpect(status().isNoContent()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(deletedPolicy.getStatus(), replayedDeletedPolicy.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(deletedPolicy.getContentAsString(), replayedDeletedPolicy.getContentAsString());
    }

    @Test void reminderCrudStatusAndReferences() throws Exception {
        String event = createEvent("event-r", "e");
        String policy = createPolicy("policy-r", "EMAIL", 5);
        String first = mvc.perform(post("/api/reminders").header("Idempotency-Key", "reminder-1").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + event + "\",\"policyId\":\"" + policy + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED")).andReturn().getResponse().getContentAsString();
        String id = node(first, "id");
        String replayedCreate = mvc.perform(post("/api/reminders").header("Idempotency-Key", "reminder-1").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + event + "\",\"policyId\":\"" + policy + "\"}" )).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(first, replayedCreate);
        mvc.perform(post("/api/reminders").header("Idempotency-Key", "reminder-1").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"different\",\"policyId\":\"" + policy + "\"}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reminders")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/reminders/" + id)).andExpect(status().isOk());
        String transitioned = mvc.perform(patch("/api/reminders/" + id + "/status").header("Idempotency-Key", "reminder-transition-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULE_PENDING\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
        String replayedTransition = mvc.perform(patch("/api/reminders/" + id + "/status").header("Idempotency-Key", "reminder-transition-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULE_PENDING\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(transitioned, replayedTransition);
        mvc.perform(patch("/api/reminders/" + id + "/status").header("Idempotency-Key", "reminder-transition-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CREATED\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/reminders/" + id + "/status").header("Idempotency-Key", "reminder-transition-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        String updatedReminder = mvc.perform(put("/api/reminders/" + id).header("Idempotency-Key", "reminder-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + event + "\",\"policyId\":\"" + policy + "\",\"expectedVersion\":1}" )).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andReturn().getResponse().getContentAsString();
        String replayedReminder = mvc.perform(put("/api/reminders/" + id).header("Idempotency-Key", "reminder-update-1").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + event + "\",\"policyId\":\"" + policy + "\",\"expectedVersion\":1}" )).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(updatedReminder, replayedReminder);
        mvc.perform(delete("/api/events/" + event).header("Idempotency-Key", "event-parent-delete").param("expectedVersion", "0")).andExpect(status().isConflict());
        mvc.perform(delete("/api/notification-policies/" + policy).header("Idempotency-Key", "policy-parent-delete").param("expectedVersion", "0")).andExpect(status().isConflict());
        mvc.perform(delete("/api/reminders/" + id).header("Idempotency-Key", "reminder-delete-1").param("expectedVersion", "1")).andExpect(status().isConflict());
        var deletedReminder = mvc.perform(delete("/api/reminders/" + id).header("Idempotency-Key", "reminder-delete-1").param("expectedVersion", "2")).andExpect(status().isNoContent()).andReturn().getResponse();
        var replayedDeletedReminder = mvc.perform(delete("/api/reminders/" + id).header("Idempotency-Key", "reminder-delete-1").param("expectedVersion", "2")).andExpect(status().isNoContent()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(deletedReminder.getStatus(), replayedDeletedReminder.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(deletedReminder.getContentAsString(), replayedDeletedReminder.getContentAsString());
    }

    @Test void concurrentSameKeyCreateReturnsSameIdAndOneRow() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> first = pool.submit(() -> createConcurrentEvent(start));
        Future<String> second = pool.submit(() -> createConcurrentEvent(start));
        start.countDown();
        HttpResult firstResult = result(first.get());
        HttpResult secondResult = result(second.get());
        pool.shutdownNow();
        org.junit.jupiter.api.Assertions.assertEquals(200, firstResult.status());
        org.junit.jupiter.api.Assertions.assertEquals(200, secondResult.status());
        org.junit.jupiter.api.Assertions.assertEquals(firstResult.body(), secondResult.body());
        String firstId = node(firstResult.body(), "id");
        String secondId = node(secondResult.body(), "id");
        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
        org.junit.jupiter.api.Assertions.assertEquals(1, db.queryForObject("select count(*) from events", Integer.class));
    }

    private String createConcurrentEvent(CountDownLatch start) throws Exception {
        start.await();
        var response = mvc.perform(post("/api/events").header("Idempotency-Key", "concurrent-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"concurrent\",\"startsAt\":\"2030-01-01T10:00:00Z\"}"))
                .andReturn().getResponse();
        return response.getStatus() + "\n" + response.getContentAsString();
    }

    private HttpResult result(String encoded) {
        int separator = encoded.indexOf('\n');
        return new HttpResult(Integer.parseInt(encoded.substring(0, separator)), encoded.substring(separator + 1));
    }

    @Test void duplicateAndValidationFailuresAreClientErrors() throws Exception {
        mvc.perform(post("/api/events").header("Idempotency-Key", "bad").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"startsAt\":\"2030-01-02T10:00:00Z\",\"endsAt\":\"2030-01-01T10:00:00Z\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/events").header("Idempotency-Key", " ").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"startsAt\":\"2030-01-01T10:00:00Z\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/events").header("Idempotency-Key", "a".repeat(201)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"startsAt\":\"2030-01-01T10:00:00Z\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/notification-policies").header("Idempotency-Key", "p").contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"EMAIL\",\"leadMinutes\":-1}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/reminders").header("Idempotency-Key", "r").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"policyId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/events").header("Idempotency-Key", "missing-title").contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":null}")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/events/" + UUID.randomUUID()).param("expectedVersion", "-1")).andExpect(status().is4xxClientError());

        String eventId = UUID.randomUUID().toString();
        String policyId = UUID.randomUUID().toString();
        String reminderId = UUID.randomUUID().toString();
        String oversized = "k".repeat(201);
        String eventUpdate = "{\"title\":\"x\",\"startsAt\":\"2030-01-01T10:00:00Z\",\"expectedVersion\":0}";
        String reminderUpdate = "{\"eventId\":\"" + eventId + "\",\"policyId\":\"" + policyId + "\",\"expectedVersion\":0}";
        String transition = "{\"status\":\"SCHEDULE_PENDING\",\"expectedVersion\":0}";
        for (String key : new String[]{" ", oversized}) {
            mvc.perform(put("/api/events/" + eventId).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(eventUpdate)).andExpect(status().is4xxClientError());
            mvc.perform(put("/api/reminders/" + reminderId).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(reminderUpdate)).andExpect(status().is4xxClientError());
            mvc.perform(patch("/api/reminders/" + reminderId + "/status").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(transition)).andExpect(status().is4xxClientError());
            mvc.perform(delete("/api/events/" + eventId).header("Idempotency-Key", key).param("expectedVersion", "0")).andExpect(status().is4xxClientError());
            mvc.perform(delete("/api/reminders/" + reminderId).header("Idempotency-Key", key).param("expectedVersion", "0")).andExpect(status().is4xxClientError());
        }
        mvc.perform(put("/api/events/" + eventId).contentType(MediaType.APPLICATION_JSON).content(eventUpdate)).andExpect(status().is4xxClientError());
        mvc.perform(patch("/api/reminders/" + reminderId + "/status").contentType(MediaType.APPLICATION_JSON).content(transition)).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/events/" + eventId).param("expectedVersion", "0")).andExpect(status().is4xxClientError());
    }

    private String createEvent(String key, String title) throws Exception {
        return node(mvc.perform(post("/api/events").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\",\"startsAt\":\"2030-01-01T10:00:00Z\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "id");
    }
    private String createPolicy(String key, String channel, int lead) throws Exception {
        return node(mvc.perform(post("/api/notification-policies").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"" + channel + "\",\"leadMinutes\":" + lead + "}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "id");
    }
    private String node(String body, String field) throws Exception { return json.readTree(body).get(field).asText(); }
}
