package com.middleproject.reminder;

import com.middleproject.reminder.security.OwnerTokenAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:single-owner-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "app.security.enabled=true",
        "app.security.owner-id=secure-owner",
        "app.security.owner-token=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class SingleOwnerSecurityIntegrationTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

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
    void protectedApiRejectsMissingAndWrongTokensButAllowsTheConfiguredOwner() throws Exception {
        mvc.perform(get("/api/deadlines"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deadlines").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mcp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deadlines").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void serverIdentityOverridesSpoofedOwnerInputOnWrite() throws Exception {
        mvc.perform(post("/api/deadlines")
                        .header("Authorization", bearer())
                        .header("X-User-Id", "attacker")
                        .header("Idempotency-Key", "secure-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"보호된 일정","startsAt":"2035-06-12T18:00:00+09:00","leadMinutes":60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("보호된 일정"));

        assertEquals("secure-owner", db.queryForObject("select owner_id from reminders", String.class));
    }

    @Test
    void enabledSecurityRefusesMissingOrShortConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> new OwnerTokenAuthenticationFilter(true, "owner", "short"));
        assertThrows(IllegalStateException.class,
                () -> new OwnerTokenAuthenticationFilter(true, "", TOKEN));
    }

    private static String bearer() {
        return "Bearer " + TOKEN;
    }
}
