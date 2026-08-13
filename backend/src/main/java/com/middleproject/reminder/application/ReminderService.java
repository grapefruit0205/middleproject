package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.ReminderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository repository;
    private final IdempotencyService idempotency;

    public ReminderService(ReminderRepository repository, IdempotencyService idempotency) {
        this.repository = repository;
        this.idempotency = idempotency;
    }
    public List<Reminder> all() { return repository.findAll(); }
    public Reminder find(UUID id) { return repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reminder not found")); }

    @Transactional
    public Reminder create(UUID event, UUID policy, String key) {
        return idempotency.execute("reminders:create", key, new Object[]{event, policy}, Reminder.class, () -> {
            try { return repository.insert(UUID.randomUUID(), event, policy); }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.BAD_REQUEST, "eventId or policyId does not exist", e); }
        });
    }

    @Transactional
    public Reminder update(UUID id, UUID event, UUID policy, long version, String key) {
        return idempotency.execute("reminders:update:" + id, key, new Object[]{event, policy, version}, Reminder.class, () -> {
            try { if (!repository.update(id, event, policy, version)) { find(id); throw conflict(); } return find(id); }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.BAD_REQUEST, "eventId or policyId does not exist", e); }
        });
    }

    @Transactional
    public Reminder transition(UUID id, ReminderStatus target, long version, String key) {
        return idempotency.execute("reminders:transition:" + id, key, new Object[]{target, version}, Reminder.class, () -> {
            Reminder current = find(id);
            if (current.version() != version) throw conflict();
            try { current.transitionTo(target); }
            catch (IllegalStateException e) { throw error(HttpStatus.CONFLICT, e.getMessage(), e); }
            if (!repository.transition(id, current.status(), target, version)) throw conflict();
            return find(id);
        });
    }

    @Transactional
    public void delete(UUID id, long version, String key) {
        idempotency.executeVoid("reminders:delete:" + id, key, version, () -> {
            if (!repository.delete(id, version)) { find(id); throw conflict(); }
        });
    }

    private ResponseStatusException conflict() { return error(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private ResponseStatusException error(HttpStatus status, String message, Throwable cause) { return new ResponseStatusException(status, message, cause); }
}
