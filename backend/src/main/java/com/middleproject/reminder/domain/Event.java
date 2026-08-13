package com.middleproject.reminder.domain;
import java.time.OffsetDateTime;
import java.util.UUID;
public record Event(UUID id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt, long version) {}
