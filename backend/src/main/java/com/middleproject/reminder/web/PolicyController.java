package com.middleproject.reminder.web;

import com.middleproject.reminder.application.PolicyService;
import com.middleproject.reminder.domain.NotificationPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notification-policies")
public class PolicyController {
    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    public record Request(
            @NotBlank @Size(max = 30) String channel,
            @NotNull @PositiveOrZero Integer leadMinutes,
            Long expectedVersion) {
    }

    @GetMapping
    public List<NotificationPolicy> list() { return service.all(); }

    @GetMapping("/{id}")
    public NotificationPolicy get(@PathVariable UUID id) { return service.find(id); }

    @PostMapping
    public NotificationPolicy create(@RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                     @Valid @RequestBody Request request) {
        requireKey(key);
        return service.create(request.channel(), request.leadMinutes(), key);
    }

    @PutMapping("/{id}")
    public NotificationPolicy update(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key, @Valid @RequestBody Request request) {
        requireKey(key);
        requireVersion(request.expectedVersion());
        return service.update(id, request.channel(), request.leadMinutes(), request.expectedVersion(), key);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key, @RequestParam Long expectedVersion) {
        requireKey(key);
        requireVersion(expectedVersion);
        service.delete(id, expectedVersion, key);
    }

    private static void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion is required and must be non-negative");
        }
    }
}
