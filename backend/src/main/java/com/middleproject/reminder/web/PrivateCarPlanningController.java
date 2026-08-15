package com.middleproject.reminder.web;

import com.middleproject.reminder.application.PrivateCarPlanningService;
import com.middleproject.reminder.domain.PrivateCarPlanningInput;
import com.middleproject.reminder.domain.PrivateCarRoute;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import java.util.UUID;

/**
 * Private-car vertical slice REST surface. GET next-question and POST route-preview are
 * read-only; POST confirm is the only way to persist a confirmed route and reminder.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/private-car")
public class PrivateCarPlanningController {

    static final String PROPOSAL_ID_PATTERN = "[0-9a-f]{64}";

    public record ConfirmRequest(
            @NotBlank @Pattern(regexp = PROPOSAL_ID_PATTERN) String proposalId,
            @NotNull OffsetDateTime previewFetchedAt,
            @NotNull @Min(PrivateCarPlanningInput.LEAD_MIN) @Max(PrivateCarPlanningInput.LEAD_MAX) Integer reminderLeadMinutes,
            @NotBlank @Size(max = 200) String confirmationId) {}

    /** Wraps the next-question identifier so the JSON body is an object, not a bare string. */
    public record NextQuestionResponse(String questionId) {}

    private final PrivateCarPlanningService service;

    public PrivateCarPlanningController(PrivateCarPlanningService service) { this.service = service; }

    @GetMapping("/next-question")
    public NextQuestionResponse nextQuestion(@PathVariable UUID tripId) {
        return new NextQuestionResponse(service.nextQuestion(tripId));
    }

    @PostMapping("/route-preview")
    public PrivateCarRoute routePreview(@PathVariable UUID tripId) {
        return service.previewRoute(tripId);
    }

    @PostMapping("/confirm")
    public PrivateCarPlanningService.ConfirmationResult confirm(@PathVariable UUID tripId,
                                                                @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                                @Valid @RequestBody ConfirmRequest request) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
        return service.confirmRoute(tripId, request.proposalId(), request.previewFetchedAt(),
                request.reminderLeadMinutes(), request.confirmationId(), key);
    }
}
