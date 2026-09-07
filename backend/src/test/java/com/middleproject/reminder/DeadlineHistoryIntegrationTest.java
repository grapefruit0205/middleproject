package com.middleproject.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.NotificationDeliveryService;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.application.SchedulerOutboxService;
import com.middleproject.reminder.port.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deadline-history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "app.demo-owner-id=history-owner",
        "scheduler.aws.enabled=false",
        "delivery.sqs.enabled=false",
        "notification.email.enabled=false"
})
@AutoConfigureMockMvc
class DeadlineHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SchedulerOutboxService outbox;
    @Autowired ReminderDeliveryService acceptance;

    @BeforeEach
    void reset() {
        db.update("delete from notification_attempt");
        db.update("delete from reminder_delivery_receipt");
        db.update("delete from schedule_outbox");
        db.update("delete from reminders");
        db.update("delete from events");
        db.update("delete from notification_policies");
        db.update("delete from idempotency_record");
    }

    @Test
    void historyShowsDisabledModePersistedScheduleAndProviderAcceptanceWithoutClaimingReceipt() throws Exception {
        JsonNode created = create();
        UUID id = UUID.fromString(created.path("id").asText());

        mvc.perform(get("/api/deadlines/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMode").value("DISABLED"))
                .andExpect(jsonPath("$.entries[?(@.kind == 'SCHEDULE' && @.status == 'PENDING')]", hasSize(1)));

        assertTrue(outbox.reconcile(10) > 0);
        String payload = db.queryForObject(
                "select payload from schedule_outbox where reminder_id=? and operation='UPSERT'",
                String.class, id);
        assertTrue(acceptance.accept(payload));
        NotificationSender sender = new NotificationSender() {
            public Channel channel() { return Channel.EMAIL; }
            public SendResult send(SendRequest request) { return new SendResult("provider-test-reference"); }
        };
        new NotificationDeliveryService(db, sender).deliver(payload);
        db.update("update notification_attempt set error_message='sensitive-provider-detail' where reminder_id=?", id);

        String response = mvc.perform(get("/api/deadlines/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMode").value("DISABLED"))
                .andExpect(jsonPath("$.entries[?(@.kind == 'SCHEDULE' && @.status == 'SUCCEEDED')]", hasSize(1)))
                .andExpect(jsonPath("$.entries[?(@.kind == 'NOTIFICATION' && @.status == 'PROVIDER_ACCEPTED')]", hasSize(1)))
                .andExpect(jsonPath("$.entries[?(@.providerReference == 'provider-test-reference')]", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        assertTrue(response.contains("실제 수신이나 열람과는 다릅니다"));
        assertFalse(response.contains("sensitive-provider-detail"));
    }

    @Test
    void unknownOrOtherOwnerDeadlineHasNoHistory() throws Exception {
        mvc.perform(get("/api/deadlines/" + UUID.randomUUID() + "/history"))
                .andExpect(status().isNotFound());
    }

    private JsonNode create() throws Exception {
        String response = mvc.perform(post("/api/deadlines")
                        .header("Idempotency-Key", "history-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"처리 이력 일정","startsAt":"2035-06-12T18:00:00+09:00","leadMinutes":60}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
