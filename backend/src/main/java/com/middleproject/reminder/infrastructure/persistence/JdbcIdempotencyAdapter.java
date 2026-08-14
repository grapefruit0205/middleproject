package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.IdempotencyPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdempotencyAdapter implements IdempotencyPort {
    private static final long LEASE_SECONDS = 30;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcIdempotencyAdapter(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String reserve(String scope, String key, String requestHash) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime lease = now.plusSeconds(LEASE_SECONDS);
        String token = UUID.randomUUID().toString();
        String sql = isH2()
                ? "insert into idempotency_record(scope,idempotency_key,request_hash,status,attempts,created_at,lease_until,last_claim_at,claim_token) select ?,?,?, 'IN_PROGRESS',0,?,?,?,? where not exists (select 1 from idempotency_record where scope=? and idempotency_key=?)"
                : "insert into idempotency_record(scope,idempotency_key,request_hash,status,attempts,created_at,lease_until,last_claim_at,claim_token) values (?,?,?,'IN_PROGRESS',0,?,?,?,?) on conflict (scope,idempotency_key) do nothing";
        try {
            int count = isH2() ? jdbc.update(sql, scope, key, requestHash, now, lease, now, token, scope, key)
                    : jdbc.update(sql, scope, key, requestHash, now, lease, now, token);
            return count == 1 ? token : null;
        } catch (DataIntegrityViolationException duplicate) { return null; }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String claimExpired(String scope, String key, String requestHash) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String token = UUID.randomUUID().toString();
        return jdbc.update("update idempotency_record set status='IN_PROGRESS',claim_token=?,lease_until=?,last_claim_at=?,last_error=null where scope=? and idempotency_key=? and status='IN_PROGRESS' and request_hash=? and lease_until <= ?", token, now.plusSeconds(LEASE_SECONDS), now, scope, key, requestHash, now) == 1 ? token : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String claimFailed(String scope, String key, String requestHash) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String token = UUID.randomUUID().toString();
        return jdbc.update("update idempotency_record set status='IN_PROGRESS',claim_token=?,lease_until=?,last_claim_at=?,last_error=null where scope=? and idempotency_key=? and status='FAILED' and request_hash=? and attempts < 10", token, now.plusSeconds(LEASE_SECONDS), now, scope, key, requestHash) == 1 ? token : null;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String key) {
        return jdbc.query("select request_hash,status,claim_token,response_status,response_body,attempts,last_error,lease_until,last_claim_at from idempotency_record where scope=? and idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), rs.getString(5), rs.getInt(6), rs.getString(7), rs.getObject(8, OffsetDateTime.class), rs.getObject(9, OffsetDateTime.class))) : Optional.empty(), scope, key);
    }

    @Override
    public boolean complete(String scope, String key, String token, int status, String body) {
        return jdbc.update("update idempotency_record set status='COMPLETED',response_status=?,response_body=?,completed_at=?,lease_until=null,last_error=null where scope=? and idempotency_key=? and claim_token=? and status='IN_PROGRESS'", status, body, OffsetDateTime.now(clock), scope, key, token) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(String scope, String key, String token, String error) {
        return jdbc.update("update idempotency_record set status='FAILED',attempts=least(attempts+1,10),lease_until=null,last_error=? where scope=? and idempotency_key=? and claim_token=? and status='IN_PROGRESS'", error == null ? "request failed" : error.substring(0, Math.min(1000, error.length())), scope, key, token) == 1;
    }

    private boolean isH2() {
        try (var c = jdbc.getDataSource().getConnection()) { return c.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2"); }
        catch (java.sql.SQLException e) { throw new IllegalStateException("Cannot identify database", e); }
    }
}
