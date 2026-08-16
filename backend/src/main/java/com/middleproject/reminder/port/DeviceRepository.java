package com.middleproject.reminder.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence for paired devices. Only SHA-256 hashes of device tokens are stored. */
public interface DeviceRepository {

    Optional<DeviceRow> findByTokenHash(String tokenHash);

    Optional<DeviceRow> findById(UUID deviceId);

    /**
     * Inserts a new device for the demo owner. Returns true when the row was created;
     * false when the owner/installation pair already exists.
     */
    boolean insert(DeviceRow row);

    /** Revokes the device and its registration; returns true when the device existed. */
    boolean revoke(UUID deviceId, Instant revokedAt);

    /** Removes the FCM registration for the device. */
    void deleteFcmRegistration(UUID deviceId);

    /**
     * Stores the FCM registration token for the device together with its SHA-256 hash.
     * The raw registration token is not an opaque device bearer token and not a Firebase
     * server credential; it is needed by the future Phase 17 server-side sender, so it is
     * persisted while the hash keeps uniqueness/querying fast and stable.
     */
    void upsertFcmRegistration(UUID deviceId, String registrationToken, String registrationTokenHash, Instant registeredAt);

    /** Lookup for the future server-side FCM sender: the raw registration token. */
    Optional<String> findFcmRegistrationToken(UUID deviceId);

    Optional<String> findFcmRegistrationTokenHash(UUID deviceId);

    /** Latest FCM registration belonging to an active, unexpired device for one owner. */
    Optional<String> findLatestActiveFcmRegistrationToken(String ownerId, Instant now);

    /** Removes the device row and its cascaded registration. */
    void deleteDevice(UUID deviceId);

    record DeviceRow(UUID id, String ownerId, String installationId, String label,
                     String tokenHash, String status, Instant expiresAt, Instant createdAt,
                     Instant revokedAt) {
        public boolean isActiveAt(Instant now) {
            return "ACTIVE".equals(status) && (expiresAt == null || now.isBefore(expiresAt));
        }
    }
}
