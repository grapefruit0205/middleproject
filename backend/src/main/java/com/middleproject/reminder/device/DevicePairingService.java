package com.middleproject.reminder.device;

import com.middleproject.reminder.application.IdempotencyService;
import com.middleproject.reminder.infrastructure.config.DemoOwnerContext;
import com.middleproject.reminder.port.DevicePairingCodeRepository;
import com.middleproject.reminder.port.DeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pairing code issuance and exchange for the fixed Demo Owner.
 * Raw pairing codes and device bearer tokens are never persisted: only salted slow hashes
 * and SHA-256 token hashes are stored. The FCM registration token is stored because the
 * Phase 17 server-side sender needs the real value; it is never logged or returned.
 * The single-active-code database guard is the duplicate-write strategy; a repeat issue
 * while a code is active fails safely because the raw code cannot be recovered from its hash.
 */
@Service
public class DevicePairingService {

    public static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;

    private final DevicePairingCodeRepository pairingCodes;
    private final DeviceRepository devices;
    private final IdempotencyService idempotency;
    private final DemoOwnerContext demoOwner;
    private final Clock clock;

    public DevicePairingService(DevicePairingCodeRepository pairingCodes, DeviceRepository devices,
                                IdempotencyService idempotency, DemoOwnerContext demoOwner, Clock clock) {
        this.pairingCodes = pairingCodes;
        this.devices = devices;
        this.idempotency = idempotency;
        this.demoOwner = demoOwner;
        this.clock = clock;
    }

    @Transactional
    public IssuedPairing issueCode() {
        Instant now = Instant.now(clock);
        pairingCodes.expire(now);
        PairingCodeSpec spec = PairingCodeSpec.generate(clock);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        DevicePairing pairing = new DevicePairing(
                slowHash(spec.code(), saltHex),
                saltHex,
                spec.issuedAt(),
                spec.expiresAt());
        if (!pairingCodes.insertActive(pairing)) {
            throw new DevicePairingException(HttpStatus.CONFLICT,
                    "A pairing code is already active; wait for it to expire or use the active code");
        }
        return new IssuedPairing(spec.code(), spec.expiresAt());
    }

    @Transactional
    public ExchangeResult exchange(String rawCode, String installationId, String label) {
        if (rawCode == null || !rawCode.matches("[A-Z0-9]{5}-[A-Z0-9]{5}")) {
            throw new DevicePairingException("Invalid pairing code");
        }
        Instant now = Instant.now(clock);
        pairingCodes.expire(now);
        DevicePairing matching = pairingCodes.findAllActive().stream()
                .filter(pairing -> {
                    String candidateHash = slowHash(rawCode, pairing.salt());
                    return MessageDigest.isEqual(
                            candidateHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            pairing.codeHash().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                })
                .findFirst()
                .orElseThrow(() -> new DevicePairingException("Invalid or expired pairing code"));
        DevicePairing consumed = pairingCodes.consume(matching.codeHash(), now)
                .orElseThrow(() -> new DevicePairingException("Invalid or expired pairing code"));
        if (!consumed.expiresAt().isAfter(now)) {
            throw new DevicePairingException("Invalid or expired pairing code");
        }
        DeviceToken token = DeviceToken.generate();
        Instant expiresAt = now.plus(TOKEN_LIFETIME);
        DeviceRepository.DeviceRow row = new DeviceRepository.DeviceRow(UUID.randomUUID(), demoOwner.ownerId(),
                installationId, label, token.sha256Hex(), "ACTIVE", expiresAt, now, null);
        if (!devices.insert(row)) {
            throw new DevicePairingException(HttpStatus.CONFLICT, "This device installation is already paired");
        }
        return new ExchangeResult(token.raw(), row.id(), expiresAt);
    }

    @Transactional
    public DeviceSession authenticate(String authorizationHeader) {
        String token = extractBearer(authorizationHeader);
        if (token == null) {
            // Generic external error: never distinguishes malformed, padded, oversized,
            // whitespace-containing, expired, revoked, or unknown credentials.
            throw new DevicePairingException("Missing or malformed bearer token");
        }
        DeviceRepository.DeviceRow row = devices.findByTokenHash(DeviceToken.hash(token))
                .orElseThrow(() -> new DevicePairingException("Missing or malformed bearer token"));
        Instant now = Instant.now(clock);
        if (!row.isActiveAt(now)) {
            throw new DevicePairingException("Missing or malformed bearer token");
        }
        return new DeviceSession(row.id(), row.ownerId(), row.installationId(), row.label());
    }

    @Transactional
    public void revoke(UUID deviceId) {
        devices.revoke(deviceId, Instant.now(clock));
        devices.deleteFcmRegistration(deviceId);
    }

    @Transactional
    public void disconnect(UUID deviceId) {
        devices.deleteFcmRegistration(deviceId);
        devices.deleteDevice(deviceId);
    }

    @Transactional
    public void registerFcmToken(UUID deviceId, String registrationToken, String idempotencyKey) {
        if (registrationToken == null || registrationToken.isBlank() || registrationToken.length() > 4096) {
            throw new DevicePairingException(HttpStatus.BAD_REQUEST, "Invalid FCM registration token");
        }
        String trimmed = registrationToken.trim();
        String scope = "device:fcm:register:" + deviceId;
        // The raw registration token is persisted for the Phase 17 server-side sender;
        // the hash keeps uniqueness/querying. Neither is ever logged or returned.
        idempotency.executeVoid(scope, idempotencyKey, trimmed, () ->
                devices.upsertFcmRegistration(deviceId, trimmed, DeviceToken.hash(trimmed), Instant.now(clock)));
    }

    @Transactional
    public void unregisterFcmToken(UUID deviceId, String idempotencyKey) {
        String scope = "device:fcm:delete:" + deviceId;
        idempotency.executeVoid(scope, idempotencyKey, "delete", () -> devices.deleteFcmRegistration(deviceId));
    }

    public String ownerId() {
        return demoOwner.ownerId();
    }

    /** Salts and slowly hashes a pairing code (PBKDF2-HMAC-SHA-256). */
    static String slowHash(String rawCode, String saltHex) {
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(rawCode.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private String extractBearer(String header) {
        if (header == null) return null;
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7);
        // Exact generated format only: 43 unpadded base64url characters, no trimming.
        if (token.length() != DeviceToken.RAW_LENGTH || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return token;
    }

    public record IssuedPairing(String code, Instant expiresAt) {}

    public record ExchangeResult(String token, UUID deviceId, Instant expiresAt) {}

    public record DeviceSession(UUID deviceId, String ownerId, String installationId, String label) {}
}
