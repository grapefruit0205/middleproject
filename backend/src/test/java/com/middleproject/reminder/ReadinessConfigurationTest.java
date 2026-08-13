package com.middleproject.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessConfigurationTest {

    @Test
    void readinessGroupIncludesReadinessStateAndDb() throws Exception {
        var loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application.yml", new ClassPathResource("application.yml"));
        var binder = new Binder(ConfigurationPropertySources.from(sources));

        List<String> include = binder
                .bind("management.endpoint.health.group.readiness.include", Bindable.listOf(String.class))
                .orElse(null);

        assertNotNull(include, "readiness group include must be configured in application.yml");
        assertEquals(List.of("readinessState", "db"), include);

        assertTrue(binder.bind("spring.datasource.url", Bindable.of(String.class)).isBound());
        assertEquals("${DB_URL}", binder.bind("spring.datasource.url", Bindable.of(String.class)).orElseThrow(AssertionError::new));
        assertEquals("${DB_USERNAME}", binder.bind("spring.datasource.username", Bindable.of(String.class)).orElseThrow(AssertionError::new));
        assertEquals("${DB_PASSWORD}", binder.bind("spring.datasource.password", Bindable.of(String.class)).orElseThrow(AssertionError::new));
        assertEquals("never", binder.bind("management.endpoint.health.show-details", Bindable.of(String.class)).orElseThrow(AssertionError::new));
    }
}
