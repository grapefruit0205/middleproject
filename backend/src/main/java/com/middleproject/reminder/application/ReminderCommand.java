package com.middleproject.reminder.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderCommand(String title, OffsetDateTime scheduledAt, String timezone,
                              boolean confirmationRequired, String ambiguityReason) {
    public ReminderCommand {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (scheduledAt == null) throw new IllegalArgumentException("scheduledAt is required");
        if (!"Asia/Seoul".equals(timezone)) throw new IllegalArgumentException("timezone must be Asia/Seoul");
        if (!confirmationRequired && ambiguityReason != null) throw new IllegalArgumentException("ambiguityReason requires confirmation");
    }
}
