package com.middleproject.reminder.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.middleproject.reminder.application.TravelRecommendationService;
import com.middleproject.reminder.domain.DepartureTiming;
import com.middleproject.reminder.domain.FollowUpConsent;
import com.middleproject.reminder.domain.PostTripRecommendationResult;
import com.middleproject.reminder.domain.RecommendationSort;
import com.middleproject.reminder.domain.TravelContextResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Travel follow-up recommendation REST surface. All three operations delegate to
 * {@link TravelRecommendationService}; this controller only maps and validates HTTP input
 * and never re-implements business rules. The POST bodies are validated against a closed
 * JSON shape at this boundary because Spring MVC's default Jackson conversion coerces
 * wrong-typed or unknown fields instead of rejecting them.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/travel")
public class TravelRecommendationController {

    private final TravelRecommendationService service;

    public TravelRecommendationController(TravelRecommendationService service) { this.service = service; }

    @PostMapping("/context")
    public TravelContextResult context(@PathVariable UUID tripId,
                                       @RequestBody JsonNode body) {
        String departureTiming = requireExactString(body, "departureTiming");
        String sort = requireExactString(body, "sort");
        return service.context(tripId, enumValue(DepartureTiming.class, departureTiming),
                enumValue(RecommendationSort.class, sort));
    }

    @PostMapping("/consent")
    public FollowUpConsent consent(@PathVariable UUID tripId,
                                   @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                   @RequestBody JsonNode body) {
        requireKey(key);
        if (body == null || !body.isObject() || body.size() != 1) {
            throw badRequest("accepted is required");
        }
        JsonNode accepted = body.get("accepted");
        if (accepted == null || !accepted.isBoolean()) {
            throw badRequest("accepted must be a JSON boolean");
        }
        return service.recordConsent(tripId, accepted.booleanValue(), key);
    }

    private static String requireExactString(JsonNode body, String name) {
        if (body == null || !body.isObject()) {
            throw badRequest("body must be a JSON object");
        }
        Set<String> allowed = Set.of("departureTiming", "sort");
        if (body.size() != allowed.size()) {
            throw badRequest("departureTiming and sort are required");
        }
        Set<String> fields = new HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(allowed)) {
            throw badRequest("departureTiming and sort are required");
        }
        JsonNode value = body.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw badRequest(name + " must be a nonblank string");
        }
        return value.textValue();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw badRequest(name + " is not one of the declared values");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @GetMapping("/recommendations")
    public PostTripRecommendationResult recommendations(@PathVariable UUID tripId,
                                                        @RequestParam @NotNull RecommendationSort sort) {
        return service.recommend(tripId, sort);
    }

    private void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }
}
