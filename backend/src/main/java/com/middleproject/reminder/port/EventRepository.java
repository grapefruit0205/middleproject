package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.Event;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
    List<Event> findAll();
    Optional<Event> findById(UUID id);
    Event insert(UUID id, String title, OffsetDateTime start, OffsetDateTime end);
    boolean update(UUID id, String title, OffsetDateTime start, OffsetDateTime end, long expectedVersion);
    boolean delete(UUID id, long expectedVersion);
}
