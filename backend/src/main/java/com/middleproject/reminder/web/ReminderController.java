package com.middleproject.reminder.web;

import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService service;

    public ReminderController(ReminderService service) { this.service = service; }

    public record Create(@NotNull UUID eventId, @NotNull UUID policyId) { }
    public record Update(@NotNull UUID eventId, @NotNull UUID policyId, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Transition(@NotNull ReminderStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }

    private void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    @GetMapping public List<Reminder> list() { return service.all(); }
    @GetMapping("/{id}") public Reminder get(@PathVariable UUID id) { return service.find(id); }

    @PostMapping
    public Reminder create(@RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                           @Valid @RequestBody Create request) {
        requireKey(key);
        return service.create(request.eventId(), request.policyId(), key);
    }

    @PutMapping("/{id}")
    public Reminder update(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key, @Valid @RequestBody Update request) {
        requireKey(key);
        return service.update(id, request.eventId(), request.policyId(), request.expectedVersion(), key);
    }

    @PatchMapping("/{id}/status")
    public Reminder transition(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key, @Valid @RequestBody Transition request) {
        requireKey(key);
        return service.transition(id, request.status(), request.expectedVersion(), key);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key, @RequestParam Long expectedVersion) {
        requireKey(key);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion is required and must be non-negative");
        }
        service.delete(id, expectedVersion, key);
    }
}
