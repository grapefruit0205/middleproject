package com.middleproject.reminder.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.domain.TripEvent;
import com.middleproject.reminder.domain.TripStatus;
import com.middleproject.reminder.port.TripEventRepository;
import com.middleproject.reminder.port.TripOutboxRepository;
import com.middleproject.reminder.port.TripRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcTripRepository implements TripRepository {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    JdbcTripRepository(JdbcTemplate db, ObjectMapper mapper) { this.db = db; this.mapper = mapper; }

    private static final String COLUMNS = "id,owner_id,departure,destination,departure_at,return_at,status,confirmation_id,draft_context,version";

    public List<Trip> findAllByOwner(String ownerId) { return db.query("select " + COLUMNS + " from trips where owner_id=? order by created_at", (r, n) -> map(r), ownerId); }
    public Optional<Trip> findByIdForOwner(UUID id, String ownerId) {
        try { return Optional.of(db.queryForObject("select " + COLUMNS + " from trips where id=? and owner_id=?", (r, n) -> map(r), id, ownerId)); }
        catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    public Trip insert(UUID id, String ownerId, String departure, String destination,
                       OffsetDateTime departureAt, OffsetDateTime returnAt, TripStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        db.update("insert into trips(id,owner_id,departure,destination,departure_at,return_at,status,draft_context,created_at,updated_at,version) values(?,?,?,?,?,?,?,'{}',?,?,0)",
                id, ownerId, departure, destination, departureAt, returnAt, status.name(), now, now);
        return findByIdForOwner(id, ownerId).orElseThrow();
    }

    public boolean addDraftAnswer(UUID id, String questionId, String answer, String draftContextJson, long version) {
        return db.update("update trips set draft_context=?,updated_at=?,version=version+1 where id=? and version=? and status='DRAFT'",
                draftContextJson, OffsetDateTime.now(), id, version) > 0;
    }

    public boolean transition(UUID id, TripStatus oldStatus, TripStatus target, long version, String confirmationId) {
        return db.update("update trips set status=?,confirmation_id=?,updated_at=?,version=version+1 where id=? and version=? and status=?",
                target.name(), confirmationId, OffsetDateTime.now(), id, version, oldStatus.name()) > 0;
    }

    private Trip map(ResultSet r) throws SQLException {
        return new Trip((UUID) r.getObject("id"), r.getString("owner_id"), r.getString("departure"), r.getString("destination"),
                r.getObject("departure_at", OffsetDateTime.class), r.getObject("return_at", OffsetDateTime.class),
                TripStatus.valueOf(r.getString("status")), r.getString("confirmation_id"), context(r), r.getLong("version"));
    }

    private Map<String, String> context(ResultSet r) {
        try {
            String raw = r.getString("draft_context");
            if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
            if (raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            return mapper.readValue(raw, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read draft context", e);
        }
    }

    String toJson(Map<String, String> context) {
        try { return mapper.writeValueAsString(context); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}

@Repository
class JdbcTripEventRepository implements TripEventRepository {
    private final JdbcTemplate db;
    JdbcTripEventRepository(JdbcTemplate db) { this.db = db; }
    public List<TripEvent> findByTrip(UUID tripId) { return db.query("select id,trip_id,type,detail,occurred_at from trip_events where trip_id=? order by occurred_at", (r, n) -> new TripEvent((UUID) r.getObject("id"), (UUID) r.getObject("trip_id"), r.getString("type"), r.getString("detail"), r.getObject("occurred_at", OffsetDateTime.class)), tripId); }
    public void insert(TripEvent event) { db.update("insert into trip_events(id,trip_id,type,detail,occurred_at,created_at) values(?,?,?,?,?,?)", event.id(), event.tripId(), event.type(), event.detail(), event.occurredAt(), OffsetDateTime.now()); }
}

@Repository
class JdbcTripOutboxRepository implements TripOutboxRepository {
    private final JdbcTemplate db;
    JdbcTripOutboxRepository(JdbcTemplate db) { this.db = db; }
    public void insert(UUID id, UUID tripId, String operation, long expectedVersion, long schedulerVersion,
                       OffsetDateTime dueAt, String payload) {
        db.update("insert into trip_outbox(id,trip_id,operation,expected_version,scheduler_version,due_at,payload,created_at) values(?,?,?,?,?,?,?,?)",
                id, tripId, operation, expectedVersion, schedulerVersion, dueAt, payload, OffsetDateTime.now());
    }
}
