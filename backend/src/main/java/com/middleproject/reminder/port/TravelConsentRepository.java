package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.FollowUpConsent;

import java.util.Optional;
import java.util.UUID;

/** Persistence for the follow-up travel recommendation consent of a trip. */
public interface TravelConsentRepository {

    /** The current consent for the trip and owner, if one exists. */
    Optional<FollowUpConsent> find(UUID tripId, String ownerId);

    /**
     * Inserts a PROPOSED consent when none exists for the trip and owner.
     * Never overwrites an existing row, including ACCEPTED/DECLINED statuses.
     * Returns the persisted consent when a row was created, or empty
     * when the trip/owner already has one.
     */
    Optional<FollowUpConsent> insertProposedIfAbsent(UUID tripId, String ownerId);

    /**
     * Moves the consent to the target status with optimistic version/update
     * semantics. Returns the new consent when the row existed at the expected
     * version, or empty on a version conflict or missing row.
     */
    Optional<FollowUpConsent> setDecision(UUID tripId, String ownerId, ConsentStatus target, long expectedVersion);
}
