package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.TravelLeg;
import com.middleproject.reminder.port.TravelLegRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcTravelLegRepository implements TravelLegRepository {
    private static final String COLUMNS = "l.id,l.day_plan_id,l.from_item_id,l.to_item_id,l.mode,l.duration_minutes,l.buffer_minutes,l.departure_at,l.arrival_at,l.provider,l.source,l.fetched_at,l.sequence,l.version";
    private final JdbcTemplate db;

    JdbcTravelLegRepository(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void insert(TravelLeg leg, String ownerId) {
        requirePlanOwner(leg.dayPlanId(), ownerId);
        String sql = "insert into travel_legs(id,day_plan_id,from_item_id,to_item_id,mode,duration_minutes,buffer_minutes,departure_at,arrival_at,provider,source,fetched_at,sequence,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, leg.id());
            ps.setObject(2, leg.dayPlanId());
            if (leg.fromItemId() == null) ps.setNull(3, java.sql.Types.OTHER); else ps.setObject(3, leg.fromItemId());
            ps.setObject(4, leg.toItemId());
            ps.setString(5, leg.mode());
            ps.setInt(6, leg.durationMinutes());
            ps.setInt(7, leg.bufferMinutes());
            ps.setObject(8, leg.departureAt());
            ps.setObject(9, leg.arrivalAt());
            ps.setString(10, leg.provider());
            ps.setString(11, leg.source());
            ps.setObject(12, leg.fetchedAt());
            ps.setInt(13, leg.sequence());
            ps.setLong(14, leg.version());
            return ps;
        });
    }

    @Override
    public List<TravelLeg> findAllByPlanForOwner(UUID dayPlanId, String ownerId) {
        return db.query("select " + COLUMNS + " from travel_legs l join day_plans p on p.id=l.day_plan_id where l.day_plan_id=? and p.owner_id=? order by l.sequence",
                (r, n) -> map(r), dayPlanId, ownerId);
    }

    private void requirePlanOwner(UUID dayPlanId, String ownerId) {
        Integer count = db.queryForObject("select count(*) from day_plans where id=? and owner_id=?", Integer.class, dayPlanId, ownerId);
        if (count == null || count != 1) throw new IllegalArgumentException("day plan is not owned by owner");
    }

    private TravelLeg map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new TravelLeg((UUID) r.getObject("id"), (UUID) r.getObject("day_plan_id"),
                (UUID) r.getObject("from_item_id"), (UUID) r.getObject("to_item_id"), r.getString("mode"),
                r.getInt("duration_minutes"), r.getInt("buffer_minutes"), r.getObject("departure_at", OffsetDateTime.class),
                r.getObject("arrival_at", OffsetDateTime.class), r.getString("provider"), r.getString("source"),
                r.getObject("fetched_at", Instant.class), r.getInt("sequence"), r.getLong("version"));
    }
}
