package com.middleproject.reminder.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

/** Provider-neutral boundary for structured reminder command JSON. */
public final class ReminderCommandSchemaValidator {
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public ReminderCommandSchemaValidator() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(new ClassPathResource("schemas/reminder-command.schema.json").getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load reminder command schema", e);
        }
    }

    public List<String> validate(JsonNode structuredCommand) {
        return schema.validate(structuredCommand).stream().map(Object::toString).toList();
    }

    public List<String> validate(ReminderCommand command) {
        var node = mapper.createObjectNode()
                .put("title", command.title())
                .put("scheduledAt", command.scheduledAt().toString())
                .put("timezone", command.timezone())
                .put("confirmationRequired", command.confirmationRequired());
        if (command.ambiguityReason() != null) node.put("ambiguityReason", command.ambiguityReason());
        return validate(node);
    }
}
