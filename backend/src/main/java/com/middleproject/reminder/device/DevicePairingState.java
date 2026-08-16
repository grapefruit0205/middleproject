package com.middleproject.reminder.device;

import java.time.Instant;

/** Mutable one-time-use state of a pairing code, including its consume transition. */
public final class DevicePairingState {

    private final String codeHash;
    private final String salt;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private boolean consumed;

    public DevicePairingState(DevicePairing pairing) {
        this(pairing.codeHash(), pairing.salt(), pairing.issuedAt(), pairing.expiresAt());
    }

    public DevicePairingState(String codeHash, String salt, Instant issuedAt, Instant expiresAt) {
        if (codeHash == null || codeHash.isBlank()) throw new IllegalArgumentException("code hash is required");
        if (salt == null || salt.isBlank()) throw new IllegalArgumentException("salt is required");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiry must be after issuance");
        }
        this.codeHash = codeHash;
        this.salt = salt;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isValidAt(Instant instant) {
        return !consumed && instant != null && !instant.isBefore(issuedAt) && instant.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumed;
    }

    /** Atomically marks the code consumed. Returns true only on the first call. */
    public boolean consumeOnce() {
        if (consumed) return false;
        consumed = true;
        return true;
    }
}
