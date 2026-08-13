package com.middleproject.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarDeploymentContractTest {

    @Test
    void applicationExtendsSpringBootServletInitializer() {
        assertTrue(SpringBootServletInitializer.class.isAssignableFrom(ReminderPlatformApplication.class),
                "ReminderPlatformApplication must extend SpringBootServletInitializer for external Tomcat deployment");
    }

    @Test
    void configureIsOverridden() throws Exception {
        var configure = ReminderPlatformApplication.class.getDeclaredMethod("configure", SpringApplicationBuilder.class);
        assertEquals(SpringApplicationBuilder.class, configure.getReturnType());
    }
}
