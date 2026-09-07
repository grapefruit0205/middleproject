package com.middleproject.reminder.web;

import com.middleproject.reminder.application.DeadlineService;
import com.middleproject.reminder.application.DeadlineHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deadlines")
public class DeadlineController {
    private final DeadlineService service;
    private final DeadlineHistoryService history;
    private final String demoOwnerId;
    private final boolean securityEnabled;

    public DeadlineController(DeadlineService service,
                              DeadlineHistoryService history,
                              @Value("${app.demo-owner-id:demo-owner}") String demoOwnerId,
                              @Value("${app.security.enabled:false}") boolean securityEnabled) {
        this.service = service;
        this.history = history;
        this.demoOwnerId = demoOwnerId;
        this.securityEnabled = securityEnabled;
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull OffsetDateTime startsAt,
            @NotNull @PositiveOrZero @Max(525_600) Integer leadMinutes) { }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull OffsetDateTime startsAt,
            @NotNull @PositiveOrZero @Max(525_600) Integer leadMinutes,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @PositiveOrZero Long expectedEventVersion,
            @NotNull @PositiveOrZero Long expectedPolicyVersion) { }

    public record CancelRequest(@NotNull @PositiveOrZero Long expectedVersion) { }

    @GetMapping
    public List<DeadlineService.DeadlineView> list(Principal principal) {
        return service.list(owner(principal));
    }

    @GetMapping("/{id}")
    public DeadlineService.DeadlineView get(@PathVariable UUID id, Principal principal) {
        return service.find(id, owner(principal));
    }

    @GetMapping("/{id}/history")
    public DeadlineHistoryService.HistoryView history(@PathVariable UUID id, Principal principal) {
        return history.find(id, owner(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineService.DeadlineView create(
            @RequestHeader("Idempotency-Key") @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody CreateRequest request,
            Principal principal) {
        requireKey(idempotencyKey);
        return service.create(
                new DeadlineService.CreateCommand(request.title(), request.startsAt(), request.leadMinutes()),
                idempotencyKey,
                owner(principal));
    }

    @PutMapping("/{id}")
    public DeadlineService.DeadlineView update(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody UpdateRequest request,
            Principal principal) {
        requireKey(idempotencyKey);
        return service.update(
                id,
                new DeadlineService.UpdateCommand(
                        request.title(),
                        request.startsAt(),
                        request.leadMinutes(),
                        request.expectedVersion(),
                        request.expectedEventVersion(),
                        request.expectedPolicyVersion()),
                idempotencyKey,
                owner(principal));
    }

    @PostMapping("/{id}/cancel")
    public DeadlineService.DeadlineView cancel(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody CancelRequest request,
            Principal principal) {
        requireKey(idempotencyKey);
        return service.cancel(id, request.expectedVersion(), idempotencyKey, owner(principal));
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private String owner(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        if (securityEnabled) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated owner is required");
        }
        return demoOwnerId;
    }
}
