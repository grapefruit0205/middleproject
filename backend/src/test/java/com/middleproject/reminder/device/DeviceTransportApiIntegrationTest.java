package com.middleproject.reminder.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.support.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:device-transport-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "trip.demo-owner-id=demo-owner", "app.transport.enabled=false"
})
@AutoConfigureMockMvc
class DeviceTransportApiIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary AdjustableClock adjustableClock() {
            return new AdjustableClock(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DevicePairingService pairing;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void clean() {
        db.update("delete from device_fcm_registration");
        db.update("delete from devices");
        db.update("delete from device_pairing_codes");
    }

    private String pair() throws Exception {
        String code = pairing.issueCode().code();
        String body = mvc.perform(post("/api/device/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\":\"" + code + "\",\"installationId\":\"transport-test\",\"label\":\"Pixel\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("token").asText();
    }

    @Test
    void everyTransportRouteRequiresTheExistingDeviceBearerToken() throws Exception {
        mvc.perform(get("/api/device/transport/subway/stations").param("name", "강남"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/transport/handoffs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pairedDeviceCanCallAllEightOperationsAndReceivesTypedDisabledOutcome() throws Exception {
        String token = pair();
        String auth = "Bearer " + token;
        String[] paths = {
                "/api/device/transport/subway/stations?name=강남",
                "/api/device/transport/subway/arrivals?stationName=강남",
                "/api/device/transport/subway/schedule?subwayStationId=MTRS123&dailyTypeCode=01&upDownTypeCode=U",
                "/api/device/transport/bus/stops/nearby?latitude=37.5665&longitude=126.9780",
                "/api/device/transport/bus/arrivals?cityCode=25&nodeId=DJB8001793",
                "/api/device/transport/bus/routes?cityCode=25&routeNo=100",
                "/api/device/transport/express-bus/arrivals?depTerminalCode=NAEK010&arrTerminalCode=NAEK020",
                "/api/device/transport/intercity-bus/schedule?depTerminalId=NAEK010&arrTerminalId=NAEK020&depPlandTime=20300101"
        };
        for (String path : paths) {
            mvc.perform(get(path).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failureKind").value("DISABLED_INSECURE"));
        }
    }

    @Test
    void invalidInputsAreRejectedBeforeProviderDispatch() throws Exception {
        String auth = "Bearer " + pair();
        String[] paths = {
                "/api/device/transport/subway/stations?name=",
                "/api/device/transport/subway/arrivals?stationName=강남&limit=0",
                "/api/device/transport/subway/schedule?subwayStationId=MTRS123&dailyTypeCode=99&upDownTypeCode=U",
                "/api/device/transport/bus/stops/nearby?latitude=0&longitude=0",
                "/api/device/transport/bus/arrivals?cityCode=0&nodeId=x",
                "/api/device/transport/bus/routes?cityCode=25&routeNo=",
                "/api/device/transport/express-bus/arrivals?depTerminalCode=x&arrTerminalCode=y&numOfRows=101",
                "/api/device/transport/intercity-bus/schedule?depTerminalId=x&arrTerminalId=y&depPlandTime=20300230"
        };
        for (String path : paths) {
            mvc.perform(get(path).header("Authorization", auth))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void handoffsExposeOnlyAllowlistedOfficialHttpsDestinations() throws Exception {
        String body = mvc.perform(get("/api/device/transport/handoffs")
                        .header("Authorization", "Bearer " + pair()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode links = mapper.readTree(body);
        assertEquals(5, links.size());
        links.forEach(value -> assertTrue(value.asText().startsWith("https://")));
        assertEquals("https://www.letskorail.com/", links.path("korail").asText());
        assertEquals("https://txbus.t-money.co.kr/", links.path("tmoneyIntercityBus").asText());
    }
}
