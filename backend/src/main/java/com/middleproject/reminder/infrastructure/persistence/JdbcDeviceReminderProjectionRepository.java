package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.DeviceReminderProjectionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDeviceReminderProjectionRepository implements DeviceReminderProjectionRepository {

    private final JdbcTemplate db;

    JdbcDeviceReminderProjectionRepository(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public Optional<UUID> tripId(UUID reminderId) {
        return db.query("select trip_id from reminders where id=?",
                rs -> rs.next() ? Optional.ofNullable(rs.getObject(1, UUID.class)) : Optional.empty(), reminderId);
    }

    @Override
    public Optional<OffsetDateTime> alarmTime(UUID reminderId) {
        return db.query(
                "select e.starts_at, p.lead_minutes from reminders r " +
                        "join events e on e.id = r.event_id " +
                        "join notification_policies p on p.id = r.policy_id where r.id=?",
                rs -> rs.next() ? Optional.of(rs.getObject(1, OffsetDateTime.class).minusMinutes(rs.getInt(2)))
                        : Optional.empty(),
                reminderId);
    }
}
