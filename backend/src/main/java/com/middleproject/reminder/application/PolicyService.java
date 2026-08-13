package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.NotificationPolicy;
import com.middleproject.reminder.port.PolicyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PolicyService {
    private final PolicyRepository repository;
    private final IdempotencyService idempotency;

    public PolicyService(PolicyRepository repository, IdempotencyService idempotency) {
        this.repository = repository;
        this.idempotency = idempotency;
    }
    public List<NotificationPolicy> all() { return repository.findAll(); }
    public NotificationPolicy find(UUID id) { return repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Policy not found")); }

    @Transactional
    public NotificationPolicy create(String channel, int leadMinutes, String key) {
        return idempotency.execute("policies:create", key, new Object[]{channel, leadMinutes}, NotificationPolicy.class,
                () -> repository.insert(UUID.randomUUID(), channel, leadMinutes));
    }

    @Transactional
    public NotificationPolicy update(UUID id, String channel, int leadMinutes, long version, String key) {
        return idempotency.execute("policies:update:" + id, key, new Object[]{channel, leadMinutes, version}, NotificationPolicy.class, () -> {
            if (!repository.update(id, channel, leadMinutes, version)) { find(id); throw conflict(); }
            return find(id);
        });
    }

    @Transactional
    public void delete(UUID id, long version, String key) {
        idempotency.executeVoid("policies:delete:" + id, key, version, () -> {
            try { if (!repository.delete(id, version)) { find(id); throw conflict(); } }
            catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Policy is referenced by a reminder", e); }
        });
    }

    private ResponseStatusException conflict() { return error(HttpStatus.CONFLICT, "Optimistic lock conflict"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private ResponseStatusException error(HttpStatus status, String message, Throwable cause) { return new ResponseStatusException(status, message, cause); }
}
