package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.DeviceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDeviceRepository implements DeviceRepository {

    private final JdbcTemplate db;

    JdbcDeviceRepository(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public Optional<DeviceRow> findByTokenHash(String tokenHash) {
        return query("select * from devices where token_hash=?", tokenHash);
    }

    @Override
    public Optional<DeviceRow> findById(UUID deviceId) {
        return query("select * from devices where id=?", deviceId);
    }

    @Override
    public boolean insert(DeviceRow row) {
        String sql = isH2()
                ? "insert into devices(id,owner_id,installation_id,label,token_hash,status,expires_at,created_at) " +
                "select ?,?,?,?,?,'ACTIVE',?,? where not exists (select 1 from devices where owner_id=? and installation_id=?)"
                : "insert into devices(id,owner_id,installation_id,label,token_hash,status,expires_at,created_at) " +
                "values (?,?,?,?,?,'ACTIVE',?,?) on conflict (owner_id,installation_id) do nothing";
        return isH2()
                ? db.update(sql, row.id(), row.ownerId(), row.installationId(), row.label(), row.tokenHash(),
                toOffset(row.expiresAt()), toOffset(row.createdAt()), row.ownerId(), row.installationId()) == 1
                : db.update(sql, row.id(), row.ownerId(), row.installationId(), row.label(), row.tokenHash(),
                toOffset(row.expiresAt()), toOffset(row.createdAt())) == 1;
    }

    @Override
    public boolean revoke(UUID deviceId, Instant revokedAt) {
        return db.update("update devices set status='REVOKED',revoked_at=? where id=? and status='ACTIVE'",
                toOffset(revokedAt), deviceId) == 1;
    }

    @Override
    public void deleteFcmRegistration(UUID deviceId) {
        db.update("delete from device_fcm_registration where device_id=?", deviceId);
    }

    @Override
    public void upsertFcmRegistration(UUID deviceId, String registrationToken, String registrationTokenHash, Instant registeredAt) {
        String sql = isH2()
                ? "merge into device_fcm_registration(device_id,registration_token,registration_token_hash,registered_at) key(device_id) values(?,?,?,?)"
                : "insert into device_fcm_registration(device_id,registration_token,registration_token_hash,registered_at) values(?,?,?,?) " +
                "on conflict (device_id) do update set registration_token=excluded.registration_token, registration_token_hash=excluded.registration_token_hash, registered_at=excluded.registered_at";
        db.update(sql, deviceId, registrationToken, registrationTokenHash, toOffset(registeredAt));
    }

    @Override
    public Optional<String> findFcmRegistrationToken(UUID deviceId) {
        return db.query("select registration_token from device_fcm_registration where device_id=?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), deviceId);
    }

    @Override
    public Optional<String> findFcmRegistrationTokenHash(UUID deviceId) {
        return db.query("select registration_token_hash from device_fcm_registration where device_id=?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), deviceId);
    }

    @Override
    public void deleteDevice(UUID deviceId) {
        db.update("delete from devices where id=?", deviceId);
    }

    private Optional<DeviceRow> query(String sql, Object... args) {
        return db.query(sql, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), args);
    }

    private DeviceRow map(ResultSet rs) throws SQLException {
        return new DeviceRow(
                rs.getObject("id", UUID.class),
                rs.getString("owner_id"),
                rs.getString("installation_id"),
                rs.getString("label"),
                rs.getString("token_hash"),
                rs.getString("status"),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("revoked_at", OffsetDateTime.class) == null
                        ? null : rs.getObject("revoked_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private boolean isH2() {
        try (var c = db.getDataSource().getConnection()) {
            return c.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot identify database", e);
        }
    }
}
