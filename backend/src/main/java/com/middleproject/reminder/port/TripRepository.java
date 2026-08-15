package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {
    List<Trip> findAllByOwner(String ownerId);
    Optional<Trip> findByIdForOwner(UUID id, String ownerId);
    Trip insert(UUID id, String ownerId, String departure, String destination,
                OffsetDateTime departureAt, OffsetDateTime returnAt, TripStatus status);
    boolean addDraftAnswer(UUID id, String questionId, String answer, String draftContextJson, long version);
    boolean transition(UUID id, TripStatus oldStatus, TripStatus target, long version, String confirmationId);
}
