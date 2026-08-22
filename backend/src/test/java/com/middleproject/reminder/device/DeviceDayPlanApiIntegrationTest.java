package com.middleproject.reminder.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.DayPlanConfirmationService;
import com.middleproject.reminder.domain.DayPlanDraft;
import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleDraftItem;
import com.middleproject.reminder.domain.ScheduleTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:device-day-plan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner"
})
@AutoConfigureMockMvc
class DeviceDayPlanApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired DevicePairingService pairing;
    @Autowired DayPlanConfirmationService confirmations;

    @BeforeEach
    void clean() {
        db.update("delete from schedule_outbox");
        db.update("delete from reminders");
        db.update("delete from notification_policies");
        db.update("delete from events");
        db.update("delete from travel_legs");
        db.update("delete from schedule_items");
        db.update("delete from day_plans");
        db.update("delete from devices");
        db.update("delete from device_pairing_codes");
        db.update("delete from idempotency_record");
    }

    @Test
    void pairedDeviceReadsTimelineAndCancelsItemThroughVersionedEndpoint() throws Exception {
        LocalDate date = LocalDate.of(2040, 6, 1);
        var plan = confirmations.confirm(draft(date), "device-plan-confirm", "device-plan-confirm-key").plan();
        String token = pair();

        mvc.perform(get("/api/device/day-plans?date=" + date).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(plan.id().toString()))
                .andExpect(jsonPath("$[0].items[0].title").value("병원"))
                .andExpect(jsonPath("$[0].items[0].reminderStatus").value("SCHEDULE_PENDING"));

        MvcResult cancel = mvc.perform(post("/api/device/day-plans/" + plan.id() + "/items/0/cancel")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "device-plan-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(plan.id().toString()))
                .andExpect(jsonPath("$.plan.version").value(3))
                .andReturn();
        JsonNode body = mapper.readTree(cancel.getResponse().getContentAsString());
        assertEquals(2, body.path("preview").path("entries").size());
        assertEquals("점심", body.path("preview").path("entries").get(1).path("title").asText());
    }

    private String pair() throws Exception {
        String code = pairing.issueCode().code();
        MvcResult result = mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"install-day-plan\",\"label\":\"Pixel\"}"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private static DayPlanDraft draft(LocalDate date) {
        OffsetDateTime start = date.atTime(9, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        OffsetDateTime second = date.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        return new DayPlanDraft(date, "Asia/Seoul", "집", "서울", new GeoPoint(37.5, 127.0),
                java.util.List.of(
                        new ScheduleDraftItem("병원", ScheduleTimeType.FIXED_START, start, start.plusMinutes(60), 60,
                                "병원", "서울", new GeoPoint(37.51, 127.01), "자차"),
                        new ScheduleDraftItem("점심", ScheduleTimeType.FIXED_START, second, second.plusMinutes(60), 60,
                                "식당", "서울", new GeoPoint(37.52, 127.02), "자차")), 15, false);
    }
}
