package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.Event;
import com.middleproject.reminder.domain.NotificationPolicy;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.EventRepository;
import com.middleproject.reminder.port.PolicyRepository;
import com.middleproject.reminder.port.ReminderRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcEventRepository implements EventRepository {
    private final JdbcTemplate db;
    JdbcEventRepository(JdbcTemplate db) { this.db = db; }
    public List<Event> findAll() { return db.query("select * from events order by created_at", (r, n) -> map(r)); }
    public Optional<Event> findById(UUID id) { return one("select * from events where id=?", id); }
    public Event insert(UUID id, String title, OffsetDateTime start, OffsetDateTime end) {
        OffsetDateTime now = OffsetDateTime.now();
        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)", id, title, start, end, now, now);
        return findById(id).orElseThrow();
    }
    public boolean update(UUID id, String title, OffsetDateTime start, OffsetDateTime end, long version) { return db.update("update events set title=?,starts_at=?,ends_at=?,updated_at=?,version=version+1 where id=? and version=?", title, start, end, OffsetDateTime.now(), id, version) > 0; }
    public boolean delete(UUID id, long version) { return db.update("delete from events where id=? and version=?", id, version) > 0; }
    private Optional<Event> one(String q, Object x) { try { return Optional.of(db.queryForObject(q, (r, n) -> map(r), x)); } catch (EmptyResultDataAccessException e) { return Optional.empty(); } }
    private Event map(java.sql.ResultSet r) throws java.sql.SQLException { return new Event((UUID) r.getObject("id"), r.getString("title"), r.getObject("starts_at", OffsetDateTime.class), r.getObject("ends_at", OffsetDateTime.class), r.getLong("version")); }
}

@Repository
class JdbcPolicyRepository implements PolicyRepository {
    private final JdbcTemplate db;
    JdbcPolicyRepository(JdbcTemplate db) { this.db = db; }
    public List<NotificationPolicy> findAll() { return db.query("select * from notification_policies order by created_at", (r, n) -> map(r)); }
    public Optional<NotificationPolicy> findById(UUID id) { return one("select * from notification_policies where id=?", id); }
    public NotificationPolicy insert(UUID id, String channel, int leadMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        db.update("insert into notification_policies(id,channel,lead_minutes,created_at,updated_at,version) values(?,?,?,?,?,0)", id, channel, leadMinutes, now, now);
        return findById(id).orElseThrow();
    }
    public boolean update(UUID id, String channel, int leadMinutes, long version) { return db.update("update notification_policies set channel=?,lead_minutes=?,updated_at=?,version=version+1 where id=? and version=?", channel, leadMinutes, OffsetDateTime.now(), id, version) > 0; }
    public boolean delete(UUID id, long version) { return db.update("delete from notification_policies where id=? and version=?", id, version) > 0; }
    private Optional<NotificationPolicy> one(String q, Object x) { try { return Optional.of(db.queryForObject(q, (r, n) -> map(r), x)); } catch (EmptyResultDataAccessException e) { return Optional.empty(); } }
    private NotificationPolicy map(java.sql.ResultSet r) throws java.sql.SQLException { return new NotificationPolicy((UUID) r.getObject("id"), r.getString("channel"), r.getInt("lead_minutes"), r.getLong("version")); }
}

@Repository
class JdbcReminderRepository implements ReminderRepository {
    private final JdbcTemplate db;
    JdbcReminderRepository(JdbcTemplate db) { this.db = db; }
    public List<Reminder> findAll() { return db.query("select * from reminders order by created_at", (r, n) -> map(r)); }
    public List<Reminder> findAllByOwner(String ownerId) { return db.query("select * from reminders where owner_id=? order by created_at", (r, n) -> map(r), ownerId); }
    public Optional<Reminder> findById(UUID id) { return one("select * from reminders where id=?", id); }
    public Optional<Reminder> findByIdForOwner(UUID id, String ownerId) { try { return Optional.of(db.queryForObject("select * from reminders where id=? and owner_id=?", (r,n) -> map(r), id, ownerId)); } catch (EmptyResultDataAccessException e) { return Optional.empty(); } }
    public Reminder insert(UUID id, UUID eventId, UUID policyId, String ownerId) {
        OffsetDateTime now = OffsetDateTime.now();
        db.update("insert into reminders(id,event_id,policy_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,0)", id, eventId, policyId, ownerId, ReminderStatus.CREATED.name(), now, now);
        return findById(id).orElseThrow();
    }
public boolean update(UUID id, UUID eventId, UUID policyId, long version) { return db.update("update reminders set event_id=?,policy_id=?,updated_at=?,version=version+1 where id=? and version=?", eventId, policyId, OffsetDateTime.now(), id, version) > 0; }
     public boolean updateForOwner(UUID id, UUID eventId, UUID policyId, long version, String ownerId) { return db.update("update reminders set event_id=?,policy_id=?,updated_at=?,version=version+1 where id=? and version=? and owner_id=?", eventId, policyId, OffsetDateTime.now(), id, version, ownerId) > 0; }
     public boolean transition(UUID id, ReminderStatus oldStatus, ReminderStatus target, long version) { return db.update("update reminders set status=?,updated_at=?,version=version+1 where id=? and version=? and status=?", target.name(), OffsetDateTime.now(), id, version, oldStatus.name()) > 0; }
     public boolean transitionForOwner(UUID id, ReminderStatus oldStatus, ReminderStatus target, long version, String ownerId) { return db.update("update reminders set status=?,updated_at=?,version=version+1 where id=? and version=? and status=? and owner_id=?", target.name(), OffsetDateTime.now(), id, version, oldStatus.name(), ownerId) > 0; }
    public boolean delete(UUID id, long version) { return db.update("delete from reminders where id=? and version=?", id, version) > 0; }
    private Optional<Reminder> one(String q, Object x) { try { return Optional.of(db.queryForObject(q, (r, n) -> map(r), x)); } catch (EmptyResultDataAccessException e) { return Optional.empty(); } }
    private Reminder map(java.sql.ResultSet r) throws java.sql.SQLException { return new Reminder((UUID) r.getObject("id"), (UUID) r.getObject("event_id"), (UUID) r.getObject("policy_id"), ReminderStatus.valueOf(r.getString("status")), r.getLong("version")); }
}
