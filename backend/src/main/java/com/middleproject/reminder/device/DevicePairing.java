package com.middleproject.reminder.device;

import java.time.Instant;

/** Pairing code record: only the salted slow hash and salt are ever stored. */
public record DevicePairing(String codeHash, String salt, Instant issuedAt, Instant expiresAt) {

    public DevicePairing {
        if (codeHash == null || codeHash.isBlank()) throw new IllegalArgumentException("code hash is required");
        if (salt == null || salt.isBlank()) throw new IllegalArgumentException("salt is required");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiry must be after issuance");
        }
    }
}
