package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.Event;
import com.middleproject.reminder.port.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository repository;
    private final IdempotencyService idempotency;

    public EventService(EventRepository repository, IdempotencyService idempotency) {
        this.repository = repository;
        this.idempotency = idempotency;
    }

    public List<Event> all() { return repository.findAll(); }
    public Event find(UUID id) { return repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Event not found")); }

    @Transactional
    public Event create(String title, OffsetDateTime start, OffsetDateTime end, String key) {
        return idempotency.execute("events:create", key, new Object[]{title, start, end}, Event.class,
                () -> repository.insert(UUID.randomUUID(), title, start, end));
    }

    @Transactional
    public Event update(UUID id, String title, OffsetDateTime start, OffsetDateTime end, long version, String key) {
        return idempotency.execute("events:update:" + id, key, new Object[]{title, start, end, version}, Event.class, () -> {
            if (!repository.update(id, title, start, end, version)) { find(id); throw conflict(); }
            return find(id);
        });
    }

    @Transactional
    public void delete(UUID id, long version, String key) {
        idempotency.executeVoid("events:delete:" + id, key, version, () -> {
            try { if (!repository.delete(id, version)) { find(id); throw conflict(); } }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Event is referenced by a reminder", e); }
        });
    }

    private ResponseStatusException conflict() { return error(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private ResponseStatusException error(HttpStatus status, String message, Throwable cause) { return new ResponseStatusException(status, message, cause); }
}
