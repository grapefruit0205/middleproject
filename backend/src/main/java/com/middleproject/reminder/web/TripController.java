package com.middleproject.reminder.web;

import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.domain.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class TripController {
    private final TripService service;

    public TripController(TripService service) { this.service = service; }

    public record DraftRequest(
            @NotBlank @Size(max = 200) String departure,
            @NotBlank @Size(max = 200) String destination,
            @NotNull OffsetDateTime departureAt,
            OffsetDateTime returnAt) {}
    public record ConfirmRequest(@NotBlank @Size(max = 200) String confirmationId) {}
    public record AnswerRequest(@NotBlank @Size(max = 200) String question, @NotBlank @Size(max = 2000) String answer) {}
    public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    private void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    @GetMapping("/api/trips")
    public List<Trip> list() { return service.all(); }

    @GetMapping("/api/trips/{id}")
    public Trip get(@PathVariable UUID id) { return service.find(id); }

    @PostMapping("/api/trip-drafts")
    public Trip createDraft(@RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                            @Valid @RequestBody DraftRequest request) {
        requireKey(key);
        validateTimes(request);
        return service.createDraft(request.departure(), request.destination(), request.departureAt(), request.returnAt(), key);
    }

    @PostMapping("/api/trips/{id}/confirm")
    public Trip confirm(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                        @Valid @RequestBody ConfirmRequest request) {
        requireKey(key);
        return service.confirm(id, request.confirmationId(), key);
    }

    @PostMapping("/api/trips/{id}/cancel")
    public Trip cancel(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                       @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return service.cancel(id, request.expectedVersion(), key);
    }

    @PostMapping("/api/trips/{id}/expire")
    public Trip expire(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                       @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return service.expire(id, request.expectedVersion(), key);
    }

    @PostMapping("/api/trips/{id}/restart")
    public Trip restart(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                        @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return service.restart(id, request.expectedVersion(), key);
    }

    @PostMapping("/api/trips/{id}/draft-context")
    public Trip answer(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                       @Valid @RequestBody AnswerRequest request) {
        requireKey(key);
        return service.answerQuestion(id, request.question(), request.answer(), key);
    }

    private static void validateTimes(DraftRequest request) {
        if (request.returnAt() != null && request.returnAt().isBefore(request.departureAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "returnAt must be greater than or equal to departureAt");
        }
    }
}
