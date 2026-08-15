package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.ConsentStatus;
import com.middleproject.reminder.domain.FollowUpConsent;
import com.middleproject.reminder.port.TravelConsentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcTravelConsentRepository implements TravelConsentRepository {

    private static final String COLUMNS = "trip_id,owner_id,status,version";

    private final JdbcTemplate db;
    private final Clock clock;

    JdbcTravelConsentRepository(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Override
    public Optional<FollowUpConsent> find(UUID tripId, String ownerId) {
        try {
            return Optional.of(db.queryForObject(
                    "select " + COLUMNS + " from travel_recommendation_consent where trip_id=? and owner_id=?",
                    (r, n) -> map(r), tripId, ownerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<FollowUpConsent> insertProposedIfAbsent(UUID tripId, String ownerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (isH2()) {
            return insertProposedIfAbsentH2(tripId, ownerId, now);
        }
        int inserted = db.update(
                "insert into travel_recommendation_consent(trip_id,owner_id,status,version,created_at,updated_at) "
                        + "values(?,?,'PROPOSED',0,?,?) on conflict (trip_id, owner_id) do nothing",
                tripId, ownerId, now, now);
        return inserted == 1 ? find(tripId, ownerId) : Optional.empty();
    }

    private Optional<FollowUpConsent> insertProposedIfAbsentH2(UUID tripId, String ownerId, OffsetDateTime now) {
        try {
            int inserted = db.update(
                    "insert into travel_recommendation_consent(trip_id,owner_id,status,version,created_at,updated_at) "
                            + "select ?,?,'PROPOSED',0,?,? where not exists "
                            + "(select 1 from travel_recommendation_consent where trip_id=? and owner_id=?)",
                    tripId, ownerId, now, now, tripId, ownerId);
            return inserted == 1 ? find(tripId, ownerId) : Optional.empty();
        } catch (DataIntegrityViolationException duplicate) {
            if (find(tripId, ownerId).isPresent()) {
                return Optional.empty();
            }
            throw duplicate;
        }
    }

    @Override
    public Optional<FollowUpConsent> setDecision(UUID tripId, String ownerId, ConsentStatus target, long expectedVersion) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        int updated = db.update(
                "update travel_recommendation_consent set status=?,version=version+1,updated_at=? "
                        + "where trip_id=? and owner_id=? and version=?",
                target.name(), OffsetDateTime.now(clock), tripId, ownerId, expectedVersion);
        return updated == 1 ? find(tripId, ownerId) : Optional.empty();
    }

    private FollowUpConsent map(ResultSet r) throws SQLException {
        return new FollowUpConsent((UUID) r.getObject("trip_id"), ConsentStatus.valueOf(r.getString("status")),
                r.getLong("version"));
    }

    private boolean isH2() {
        try (var connection = db.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot identify database", e);
        }
    }
}
