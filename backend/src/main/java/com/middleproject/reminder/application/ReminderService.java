package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.Event;
import com.middleproject.reminder.domain.NotificationPolicy;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.ReminderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository repository;
    private final IdempotencyService idempotency;
    private final JdbcTemplate db;
    private final ObjectMapper objectMapper;

    public ReminderService(ReminderRepository repository, IdempotencyService idempotency, JdbcTemplate db, ObjectMapper objectMapper) {
        this.repository = repository; this.idempotency = idempotency; this.db = db; this.objectMapper = objectMapper;
    }
    public List<Reminder> all() { return repository.findAll(); }
    public Reminder find(UUID id) { return repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reminder not found")); }

    @Transactional
    public Reminder create(UUID event, UUID policy, String key) {
        return idempotency.execute("reminders:create", key, new Object[]{event, policy}, Reminder.class, () -> {
            try { Reminder r = repository.insert(UUID.randomUUID(), event, policy); enqueue(r.id(), "UPSERT", 0, 1); return r; }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.BAD_REQUEST, "eventId or policyId does not exist", e); }
        });
    }
    @Transactional
    public Reminder update(UUID id, UUID event, UUID policy, long version, String key) {
        return idempotency.execute("reminders:update:" + id, key, new Object[]{event, policy, version}, Reminder.class, () -> {
            try { if (!repository.update(id, event, policy, version)) { find(id); throw conflict(); } Reminder r = find(id); enqueue(id, "UPSERT", r.version(), r.version() + 1); return r; }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.BAD_REQUEST, "eventId or policyId does not exist", e); }
        });
    }
    @Transactional
    public Reminder transition(UUID id, ReminderStatus target, long version, String key) {
        return idempotency.execute("reminders:transition:" + id, key, new Object[]{target, version}, Reminder.class, () -> {
            Reminder current = find(id); if (current.version() != version) throw conflict();
            try { current.transitionTo(target); } catch (IllegalStateException e) { throw error(HttpStatus.CONFLICT, e.getMessage(), e); }
            if (!repository.transition(id, current.status(), target, version)) throw conflict();
            Reminder updated = find(id); enqueue(id, target == ReminderStatus.CANCELLED ? "DELETE" : "UPSERT", updated.version(), target == ReminderStatus.CANCELLED ? updated.version() : updated.version() + 1); return updated;
        });
    }
    @Transactional
    public void delete(UUID id, long version, String key) {
        idempotency.executeVoid("reminders:delete:" + id, key, version, () -> { find(id); if (!repository.delete(id, version)) throw conflict(); enqueue(id, "DELETE", version, version); });
    }
    private void enqueue(UUID reminderId, String operation, long expectedVersion, long schedulerVersion) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("reminderId", reminderId.toString(), "schedulerVersion", schedulerVersion, "idempotencyKey", reminderId + ":" + schedulerVersion));
            OffsetDateTime dueAt = OffsetDateTime.now();
            if ("UPSERT".equals(operation)) {
                Event event = db.queryForObject("select starts_at from events where id=(select event_id from reminders where id=?)", (r, n) -> new Event(reminderId, "", r.getObject(1, OffsetDateTime.class), null, 0), reminderId);
                int lead = db.queryForObject("select lead_minutes from notification_policies where id=(select policy_id from reminders where id=?)", Integer.class, reminderId);
                dueAt = event.startsAt().minusMinutes(lead);
            }
            db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload) values(?,?,?,?,?,?,?)", UUID.randomUUID(), reminderId, operation, expectedVersion, schedulerVersion, dueAt, payload);
        } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to encode scheduler payload", e); }
    }
    private ResponseStatusException conflict() { return error(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private ResponseStatusException error(HttpStatus status, String message, Throwable cause) { return new ResponseStatusException(status, message, cause); }
}
