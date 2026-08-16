package com.middleproject.reminder.device;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** A human-enterable pairing code with an exactly 5-minute lifetime. */
public record PairingCodeSpec(String code, Instant issuedAt, Instant expiresAt) {

    public static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public PairingCodeSpec {
        if (code == null || !code.matches("[A-Z0-9]{5}-[A-Z0-9]{5}")) {
            throw new IllegalArgumentException("pairing code must match [A-Z0-9]{5}-[A-Z0-9]{5}");
        }
        if (issuedAt == null || expiresAt == null || !expiresAt.equals(issuedAt.plus(LIFETIME))) {
            throw new IllegalArgumentException("pairing code must expire exactly 5 minutes after issuance");
        }
    }

    public static PairingCodeSpec generate(Clock clock) {
        Instant issuedAt = Instant.now(clock);
        StringBuilder raw = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            raw.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        String code = raw.substring(0, 5) + "-" + raw.substring(5);
        return new PairingCodeSpec(code, issuedAt, issuedAt.plus(LIFETIME));
    }

    public boolean isValidAt(Instant instant) {
        return instant != null && !instant.isBefore(issuedAt) && instant.isBefore(expiresAt);
    }
}
