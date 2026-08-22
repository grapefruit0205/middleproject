package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ScheduleItem;
import com.middleproject.reminder.domain.ScheduleItemStatus;
import com.middleproject.reminder.domain.ScheduleTimeType;
import com.middleproject.reminder.port.ScheduleItemRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcScheduleItemRepository implements ScheduleItemRepository {
    private static final String COLUMNS = "s.id,s.day_plan_id,s.title,s.time_type,s.starts_at,s.ends_at,s.duration_minutes,s.place_name,s.address,s.latitude,s.longitude,s.sequence,s.status,s.version";
    private final JdbcTemplate db;
    private final Clock clock;

    JdbcScheduleItemRepository(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Override
    public void insert(ScheduleItem item, String ownerId) {
        requirePlanOwner(item.dayPlanId(), ownerId);
        String sql = "insert into schedule_items(id,day_plan_id,title,time_type,starts_at,ends_at,duration_minutes,place_name,address,latitude,longitude,sequence,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int i = 1;
            ps.setObject(i++, item.id());
            ps.setObject(i++, item.dayPlanId());
            ps.setString(i++, item.title());
            ps.setString(i++, item.timeType().name());
            setOffsetDateTime(ps, i++, item.startsAt());
            setOffsetDateTime(ps, i++, item.endsAt());
            ps.setInt(i++, item.durationMinutes());
            ps.setString(i++, item.placeName());
            setString(ps, i++, item.address());
            if (item.coordinates() == null) {
                ps.setNull(i++, Types.DOUBLE);
                ps.setNull(i++, Types.DOUBLE);
            } else {
                ps.setDouble(i++, item.coordinates().latitude());
                ps.setDouble(i++, item.coordinates().longitude());
            }
            ps.setInt(i++, item.sequence());
            ps.setString(i++, item.status().name());
            OffsetDateTime now = OffsetDateTime.now(clock);
            ps.setObject(i++, now);
            ps.setObject(i++, now);
            ps.setLong(i, item.version());
            return ps;
        });
    }

    @Override
    public List<ScheduleItem> findAllByPlanForOwner(UUID dayPlanId, String ownerId) {
        return db.query("select " + COLUMNS + " from schedule_items s join day_plans p on p.id=s.day_plan_id where s.day_plan_id=? and p.owner_id=? order by s.sequence",
                (r, n) -> map(r), dayPlanId, ownerId);
    }

    @Override
    public Optional<ScheduleItem> findByIdForOwner(UUID id, String ownerId) {
        try {
            return Optional.of(db.queryForObject("select " + COLUMNS + " from schedule_items s join day_plans p on p.id=s.day_plan_id where s.id=? and p.owner_id=?",
                    (r, n) -> map(r), id, ownerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean transition(UUID id, String ownerId, ScheduleItemStatus oldStatus, ScheduleItemStatus target, long version) {
        return db.update("update schedule_items set status=?,updated_at=?,version=version+1 where id=? and day_plan_id in (select id from day_plans where owner_id=?) and status=? and version=?",
                target.name(), OffsetDateTime.now(clock), id, ownerId, oldStatus.name(), version) > 0;
    }

    private void requirePlanOwner(UUID dayPlanId, String ownerId) {
        Integer count = db.queryForObject("select count(*) from day_plans where id=? and owner_id=?", Integer.class, dayPlanId, ownerId);
        if (count == null || count != 1) throw new IllegalArgumentException("day plan is not owned by owner");
    }

    private ScheduleItem map(java.sql.ResultSet r) throws java.sql.SQLException {
        Double latitude = r.getObject("latitude", Double.class);
        Double longitude = r.getObject("longitude", Double.class);
        return new ScheduleItem((UUID) r.getObject("id"), (UUID) r.getObject("day_plan_id"), r.getString("title"),
                ScheduleTimeType.valueOf(r.getString("time_type")), r.getObject("starts_at", OffsetDateTime.class),
                r.getObject("ends_at", OffsetDateTime.class), r.getInt("duration_minutes"), r.getString("place_name"),
                r.getString("address"), latitude == null || longitude == null ? null : new GeoPoint(latitude, longitude), r.getInt("sequence"),
                ScheduleItemStatus.valueOf(r.getString("status")), r.getLong("version"));
    }

    private static void setOffsetDateTime(PreparedStatement ps, int index, OffsetDateTime value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE); else ps.setObject(index, value);
    }

    private static void setString(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }
}
