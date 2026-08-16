package com.middleproject.reminder.device;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure domain rules for pairing codes and device tokens; no Spring, no database. */
class DevicePairingDomainTest {

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

    private Clock clock(Instant now) {
        return Clock.fixed(now, UTC);
    }

    @Test
    void pairingCodeIsHumanEnterableAndRandom() {
        PairingCodeSpec spec = PairingCodeSpec.generate(clock(T0));
        String code = spec.code();
        // Human-enterable: fixed-length groups of unambiguous uppercase letters/digits,
        // formatted with a separator, no lower-case letters or separator characters in the raw value.
        assertTrue(code.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"), "unexpected pairing code format: " + code);
        assertFalse(code.contains("O") && code.contains("0"));
        // Cryptographically random: two generated codes differ.
        assertNotEquals(code, PairingCodeSpec.generate(clock(T0)).code());
    }

    @Test
    void pairingCodeExpiresAtExactlyFiveMinutes() {
        PairingCodeSpec spec = PairingCodeSpec.generate(clock(T0));
        assertEquals(T0.plus(Duration.ofMinutes(5)), spec.expiresAt(), "expiry must be exactly 5 minutes after issuance");
        assertTrue(spec.isValidAt(T0));
        assertTrue(spec.isValidAt(T0.plus(Duration.ofMinutes(5).minusSeconds(1))));
        // At exactly 5 minutes the code is expired: expiry is exclusive.
        assertFalse(spec.isValidAt(T0.plus(Duration.ofMinutes(5))), "code must be expired exactly at the 5-minute boundary");
        assertFalse(spec.isValidAt(T0.plus(Duration.ofMinutes(6))));
    }

    @Test
    void deviceTokenHasAtLeast256BitsOfEntropyAndIsOpaque() {
        DeviceToken token = DeviceToken.generate();
        assertEquals(43, token.raw().length(), "base64url 32 random bytes must be exactly 43 characters");
        assertTrue(token.raw().matches("[A-Za-z0-9_-]{43}"), "token must be opaque base64url without padding");
        // Two tokens never collide and a token cannot be recovered from its hash.
        assertNotEquals(token.raw(), DeviceToken.generate().raw());
        assertFalse(token.raw().contains("="), "token must not contain padding characters");
    }

    @Test
    void deviceTokenHashIsDeterministicAndIrreversible() {
        DeviceToken token = DeviceToken.generate();
        assertEquals(64, token.sha256Hex().length());
        assertEquals(token.sha256Hex(), DeviceToken.hash(token.raw()));
        assertFalse(token.sha256Hex().contains(token.raw()));
    }

    @Test
    void deviceTokenRejectsMalformedOversizedPaddedAndWhitespaceValues() {
        String valid = DeviceToken.generate().raw();
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(null));
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(""));
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken("short"));
        // Oversized: 44 unpadded characters cannot represent exactly 32 bytes.
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid + "A"));
        // Padded base64url is not the generated format.
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid.substring(0, 42) + "=="));
        // Whitespace around or inside the token is not the generated format.
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(" " + valid));
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid + " "));
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid.substring(0, 21) + " " + valid.substring(21)));
        // Non-base64url characters are rejected.
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid.substring(0, 42) + "/"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceToken(valid.substring(0, 42) + "+"));
        // Any generated token round-trips through the strict validation.
        assertNotNull(DeviceToken.generate());
    }

    @Test
    void issuedPairingIsOneTimeAndCannotBeReconsumed() {
        DevicePairingState pairing = new DevicePairingState(new DevicePairing("code", "salt", T0, T0.plus(Duration.ofMinutes(5))));
        assertTrue(pairing.consumeOnce(), "first consume must succeed");
        assertFalse(pairing.consumeOnce(), "second consume must fail: one-time code");
        assertFalse(pairing.consumeOnce());
    }

    @Test
    void consumedPairingIsNeverValidEvenBeforeExpiry() {
        DevicePairingState pairing = new DevicePairingState(new DevicePairing("code", "salt", T0, T0.plus(Duration.ofMinutes(5))));
        pairing.consumeOnce();
        assertFalse(pairing.isValidAt(T0.plusSeconds(1)), "consumed pairing must never be valid again");
        assertTrue(pairing.isConsumed());
    }

    @Test
    void pairingRejectsInvalidStatesAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> new DevicePairing("", "s", T0, T0.plus(Duration.ofMinutes(5))));
        assertThrows(IllegalArgumentException.class, () -> new DevicePairing("c", "", T0, T0.plus(Duration.ofMinutes(5))));
        assertThrows(IllegalArgumentException.class, () -> new DevicePairing("c", "s", null, T0.plus(Duration.ofMinutes(5))));
        assertThrows(IllegalArgumentException.class, () -> new DevicePairing("c", "s", T0, null));
        // Expiry must come after issuance.
        assertThrows(IllegalArgumentException.class, () -> new DevicePairing("c", "s", T0, T0.minusSeconds(1)));
    }

    @Test
    void issuedPairingStoresOnlyHashAndSaltNeverTheRawCode() {
        DevicePairing pairing = new DevicePairing("sha256hex", "salt", T0, T0.plus(Duration.ofMinutes(5)));
        assertFalse(pairing.codeHash().contains("ABC12-DEF34"));
        assertFalse(pairing.salt().contains("ABC12-DEF34"));
    }
}
