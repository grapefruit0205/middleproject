package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.IdempotencyPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcIdempotencyAdapter implements IdempotencyPort {
    private final JdbcTemplate jdbc;

    public JdbcIdempotencyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reserve(String scope, String key, String requestHash) {
        String sql = isH2()
                ? "insert into idempotency_record(scope,idempotency_key,request_hash,created_at) select ?,?,?,? where not exists (select 1 from idempotency_record where scope=? and idempotency_key=?)"
                : "insert into idempotency_record(scope,idempotency_key,request_hash,created_at) values (?,?,?,?) on conflict (scope,idempotency_key) do nothing";
        OffsetDateTime now = OffsetDateTime.now();
        try {
            return isH2()
                    ? jdbc.update(sql, scope, key, requestHash, now, scope, key) == 1
                    : jdbc.update(sql, scope, key, requestHash, now) == 1;
        } catch (DataIntegrityViolationException duplicate) {
            find(scope, key);
            return false;
        }
    }

    private boolean isH2() {
        try (var connection = jdbc.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot identify database", e);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String key) {
        return jdbc.query("select request_hash,response_status,response_body from idempotency_record where scope=? and idempotency_key=? for update",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3))) : Optional.empty(), scope, key);
    }

    @Override
    public void complete(String scope, String key, int status, String body) {
        jdbc.update("update idempotency_record set response_status=?,response_body=?,completed_at=? where scope=? and idempotency_key=?",
                status, body, OffsetDateTime.now(), scope, key);
    }
}
