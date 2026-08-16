package com.middleproject.reminder.port;

import com.middleproject.reminder.device.DevicePairing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for one-time pairing codes. Only salted hashes are stored. */
public interface DevicePairingCodeRepository {

    /**
     * Inserts a new active pairing code. Returns true when this call created the active
     * code; false when another active code already exists (the caller must fail safely
     * because the raw code cannot be recovered from its hash).
     */
    boolean insertActive(DevicePairing pairing);

    /** All active codes with their salts, so the service can verify a presented code. */
    List<DevicePairing> findAllActive();

    /** Atomically consumes the code when it is still active. */
    Optional<DevicePairing> consume(String codeHash, Instant consumedAt);

    /** Marks active-but-expired codes as EXPIRED; returns the number of rows changed. */
    int expire(Instant now);
}
