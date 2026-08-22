package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.DayPlan;
import com.middleproject.reminder.domain.DayPlanStatus;
import com.middleproject.reminder.port.DayPlanRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDayPlanRepository implements DayPlanRepository {
    private static final String COLUMNS = "id,owner_id,plan_date,timezone,status,version";
    private final JdbcTemplate db;
    private final Clock clock;

    JdbcDayPlanRepository(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Override
    public DayPlan insert(DayPlan plan) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        db.update("insert into day_plans(id,owner_id,plan_date,timezone,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?)",
                plan.id(), plan.ownerId(), plan.planDate(), plan.timezone(), plan.status().name(), now, now, plan.version());
        return findByIdForOwner(plan.id(), plan.ownerId()).orElseThrow();
    }

    @Override
    public List<DayPlan> findAllByOwnerAndDate(String ownerId, LocalDate planDate) {
        return db.query("select " + COLUMNS + " from day_plans where owner_id=? and plan_date=? order by id",
                (r, n) -> map(r), ownerId, planDate);
    }

    @Override
    public Optional<DayPlan> findByIdForOwner(UUID id, String ownerId) {
        try {
            return Optional.of(db.queryForObject("select " + COLUMNS + " from day_plans where id=? and owner_id=?",
                    (r, n) -> map(r), id, ownerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean transition(UUID id, String ownerId, DayPlanStatus oldStatus, DayPlanStatus target, long version) {
        return db.update("update day_plans set status=?,updated_at=?,version=version+1 where id=? and owner_id=? and status=? and version=?",
                target.name(), OffsetDateTime.now(clock), id, ownerId, oldStatus.name(), version) > 0;
    }

    private DayPlan map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new DayPlan((UUID) r.getObject("id"), r.getString("owner_id"),
                r.getObject("plan_date", LocalDate.class), r.getString("timezone"),
                DayPlanStatus.valueOf(r.getString("status")), r.getLong("version"));
    }
}
