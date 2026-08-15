package com.middleproject.reminder.domain;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record Trip(UUID id, String ownerId, String departure, String destination,
                   OffsetDateTime departureAt, OffsetDateTime returnAt, TripStatus status,
                   String confirmationId, Map<String, String> draftContext, long version) {

    public Trip {
        draftContext = draftContext == null ? Map.of() : Map.copyOf(draftContext);
    }

    private Trip withStatus(TripStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid trip transition: " + status + " -> " + target);
        }
        return new Trip(id, ownerId, departure, destination, departureAt, returnAt, target, confirmationId, draftContext, version + 1);
    }

    public Trip toAwaitingConfirmation() {
        return withStatus(TripStatus.AWAITING_CONFIRMATION);
    }

    public Trip confirm(String confirmationId) {
        if (confirmationId == null || confirmationId.isBlank()) {
            throw new IllegalArgumentException("confirmationId is required");
        }
        Trip transitioned = withStatus(TripStatus.CONFIRMED);
        return new Trip(transitioned.id(), transitioned.ownerId(), transitioned.departure(), transitioned.destination(),
                transitioned.departureAt(), transitioned.returnAt(), transitioned.status(), confirmationId,
                transitioned.draftContext(), transitioned.version());
    }

    public Trip answer(String questionId, String answer) {
        if (questionId == null || questionId.isBlank() || answer == null) {
            throw new IllegalArgumentException("question and answer are required");
        }
        if (status != TripStatus.DRAFT) {
            throw new IllegalStateException("Draft context can only be accumulated on a DRAFT trip");
        }
        Map<String, String> merged = new HashMap<>(draftContext);
        merged.put(questionId, answer);
        return new Trip(id, ownerId, departure, destination, departureAt, returnAt, status, confirmationId, merged, version + 1);
    }

    public Trip cancel() {
        return withStatus(TripStatus.CANCELLED);
    }

    public Trip expire() {
        return withStatus(TripStatus.EXPIRED);
    }

    public Trip restart() {
        return withStatus(TripStatus.DRAFT);
    }
}
