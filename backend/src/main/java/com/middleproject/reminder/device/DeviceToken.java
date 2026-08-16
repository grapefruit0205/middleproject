package com.middleproject.reminder.device;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Cryptographically random opaque device token: exactly 32 bytes encoded as 43 unpadded
 * base64url characters. Bearer credentials are accepted only in this exact generated
 * format; anything else is rejected before any lookup so no format details leak.
 */
public record DeviceToken(String raw) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_BYTES = 32;
    public static final int RAW_LENGTH = 43;

    public DeviceToken {
        if (raw == null || raw.length() != RAW_LENGTH || !raw.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("device token must be exactly 43 unpadded base64url characters encoding 32 bytes");
        }
    }

    public static DeviceToken generate() {
        byte[] bytes = new byte[RAW_BYTES];
        RANDOM.nextBytes(bytes);
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new DeviceToken(encoded);
    }

    public String sha256Hex() {
        return hash(raw);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
