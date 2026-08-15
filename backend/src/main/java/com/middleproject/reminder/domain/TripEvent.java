package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripEvent(UUID id, UUID tripId, String type, String detail, OffsetDateTime occurredAt) {
}
