package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.device.DevicePairing;
import com.middleproject.reminder.port.DevicePairingCodeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcDevicePairingCodeRepository implements DevicePairingCodeRepository {

    private final JdbcTemplate db;

    JdbcDevicePairingCodeRepository(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public boolean insertActive(DevicePairing pairing) {
        // The only intended duplicate is the single-active-slot guard. An unrelated
        // integrity failure (bad FK, bad value) must propagate, not masquerade as a
        // duplicate-slot conflict. PostgreSQL: ON CONFLICT on the unique index.
        String sql = isH2()
                ? "insert into device_pairing_codes(code_hash,salt,active_slot,status,issued_at,expires_at) " +
                "select ?,?,1,'ACTIVE',?,? where not exists (select 1 from device_pairing_codes where active_slot=1)"
                : "insert into device_pairing_codes(code_hash,salt,active_slot,status,issued_at,expires_at) " +
                "values (?,?,1,'ACTIVE',?,?) on conflict (active_slot) do nothing";
        try {
            return isH2()
                    ? db.update(sql, pairing.codeHash(), pairing.salt(),
                    toOffset(pairing.issuedAt()), toOffset(pairing.expiresAt())) == 1
                    : db.update(sql, pairing.codeHash(), pairing.salt(),
                    toOffset(pairing.issuedAt()), toOffset(pairing.expiresAt())) == 1;
        } catch (DataIntegrityViolationException duplicate) {
            // H2's INSERT...SELECT...WHERE NOT EXISTS is not atomic under concurrent
            // writers: the loser of the single-active-slot race violates the unique
            // index instead of updating zero rows. That duplicate is the same safe
            // "already active" outcome, so it must return false, not leak as a raw
            // database exception. Confirm an active code actually exists before
            // classifying it as the intended duplicate; an unrelated integrity
            // failure (bad FK, bad value) must still propagate.
            if (!findAllActive().isEmpty()) {
                return false;
            }
            throw duplicate;
        }
    }

    @Override
    public List<DevicePairing> findAllActive() {
        return db.query("select code_hash,salt,issued_at,expires_at from device_pairing_codes where status='ACTIVE'",
                (rs, n) -> map(rs));
    }

    @Override
    public Optional<DevicePairing> consume(String codeHash, Instant consumedAt) {
        // Release the single-active slot to NULL so historical CONSUMED rows coexist.
        int updated = db.update(
                "update device_pairing_codes set status='CONSUMED',consumed_at=?,active_slot=null " +
                        "where code_hash=? and status='ACTIVE'",
                toOffset(consumedAt), codeHash);
        if (updated == 0) return Optional.empty();
        return db.query("select code_hash,salt,issued_at,expires_at from device_pairing_codes where code_hash=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), codeHash);
    }

    @Override
    public int expire(Instant now) {
        // Release the single-active slot to NULL so historical EXPIRED rows coexist.
        return db.update(
                "update device_pairing_codes set status='EXPIRED',active_slot=null " +
                        "where status='ACTIVE' and expires_at <= ?",
                toOffset(now));
    }

    private DevicePairing map(ResultSet rs) throws SQLException {
        return new DevicePairing(rs.getString("code_hash"), rs.getString("salt"),
                rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
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
