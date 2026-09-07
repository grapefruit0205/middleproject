package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.ReminderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeadlineService {
    private static final int MAX_LEAD_MINUTES = 525_600;

    private final JdbcTemplate db;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeadlineService(JdbcTemplate db, IdempotencyService idempotency, ObjectMapper objectMapper, Clock clock) {
        this.db = db;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record CreateCommand(String title, OffsetDateTime startsAt, int leadMinutes) { }

    public record UpdateCommand(
            String title,
            OffsetDateTime startsAt,
            int leadMinutes,
            long expectedVersion,
            long expectedEventVersion,
            long expectedPolicyVersion) { }

    public record DeadlineView(
            UUID id,
            UUID eventId,
            UUID policyId,
            String title,
            OffsetDateTime startsAt,
            int leadMinutes,
            OffsetDateTime remindAt,
            ReminderStatus status,
            String scheduleStatus,
            long version,
            long eventVersion,
            long policyVersion) { }

    public List<DeadlineView> list(String ownerId) {
        return db.query("""
                select r.id, r.event_id, r.policy_id, e.title, e.starts_at, p.lead_minutes,
                       r.status, r.version, e.version as event_version, p.version as policy_version,
                       coalesce((select o.status from schedule_outbox o where o.reminder_id=r.id
                                 order by o.created_at desc, o.id desc limit 1), 'NOT_REQUESTED') as schedule_status
                  from reminders r
                  join events e on e.id=r.event_id
                  join notification_policies p on p.id=r.policy_id
                 where r.owner_id=?
                 order by case when r.status='CANCELLED' then 1 else 0 end, e.starts_at, r.created_at
                """, (row, number) -> map(row), ownerId);
    }

    public DeadlineView find(UUID reminderId, String ownerId) {
        List<DeadlineView> rows = db.query("""
                select r.id, r.event_id, r.policy_id, e.title, e.starts_at, p.lead_minutes,
                       r.status, r.version, e.version as event_version, p.version as policy_version,
                       coalesce((select o.status from schedule_outbox o where o.reminder_id=r.id
                                 order by o.created_at desc, o.id desc limit 1), 'NOT_REQUESTED') as schedule_status
                  from reminders r
                  join events e on e.id=r.event_id
                  join notification_policies p on p.id=r.policy_id
                 where r.id=? and r.owner_id=?
                """, (row, number) -> map(row), reminderId, ownerId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deadline not found");
        }
        return rows.getFirst();
    }

    @Transactional
    public DeadlineView create(CreateCommand command, String idempotencyKey, String ownerId) {
        requireOwner(ownerId);
        validateFields(command == null ? null : command.title(),
                command == null ? null : command.startsAt(),
                command == null ? -1 : command.leadMinutes());
        return idempotency.execute(
                "deadlines:create:" + ownerId,
                idempotencyKey,
                command,
                DeadlineView.class,
                () -> insert(command, ownerId));
    }

    @Transactional
    public DeadlineView update(UUID reminderId, UpdateCommand command, String idempotencyKey, String ownerId) {
        requireOwner(ownerId);
        if (reminderId == null || command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline update is required");
        }
        validateFields(command.title(), command.startsAt(), command.leadMinutes());
        validateVersion(command.expectedVersion());
        validateVersion(command.expectedEventVersion());
        validateVersion(command.expectedPolicyVersion());
        return idempotency.execute(
                "deadlines:update:" + ownerId + ":" + reminderId,
                idempotencyKey,
                command,
                DeadlineView.class,
                () -> applyUpdate(reminderId, command, ownerId));
    }

    @Transactional
    public DeadlineView cancel(UUID reminderId, long expectedVersion, String idempotencyKey, String ownerId) {
        requireOwner(ownerId);
        if (reminderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline id is required");
        }
        validateVersion(expectedVersion);
        return idempotency.execute(
                "deadlines:cancel:" + ownerId + ":" + reminderId,
                idempotencyKey,
                Map.of("expectedVersion", expectedVersion),
                DeadlineView.class,
                () -> applyCancellation(reminderId, expectedVersion, ownerId));
    }

    private DeadlineView insert(CreateCommand command, String ownerId) {
        UUID eventId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);

        db.update("insert into events(id,title,starts_at,ends_at,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
                eventId, command.title().trim(), command.startsAt(), null, now, now);
        db.update("insert into notification_policies(id,channel,lead_minutes,created_at,updated_at,version) values(?,?,?,?,?,0)",
                policyId, "EMAIL", command.leadMinutes(), now, now);
        db.update("insert into reminders(id,event_id,policy_id,owner_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,0)",
                reminderId, eventId, policyId, ownerId, ReminderStatus.CREATED.name(), now, now);
        enqueue(reminderId, "UPSERT", 0, 1,
                command.startsAt().minusMinutes(command.leadMinutes()), now);
        return find(reminderId, ownerId);
    }

    private DeadlineView applyUpdate(UUID reminderId, UpdateCommand command, String ownerId) {
        DeadlineView current = find(reminderId, ownerId);
        if (current.version() != command.expectedVersion()
                || current.eventVersion() != command.expectedEventVersion()
                || current.policyVersion() != command.expectedPolicyVersion()) {
            throw conflict();
        }
        if (!canChange(current.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deadline can no longer be changed in status " + current.status());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        int eventUpdated = db.update("""
                update events set title=?,starts_at=?,updated_at=?,version=version+1
                 where id=? and version=?
                """, command.title().trim(), command.startsAt(), now,
                current.eventId(), command.expectedEventVersion());
        int policyUpdated = db.update("""
                update notification_policies set lead_minutes=?,updated_at=?,version=version+1
                 where id=? and version=?
                """, command.leadMinutes(), now,
                current.policyId(), command.expectedPolicyVersion());
        int reminderUpdated = db.update("""
                update reminders set status='SCHEDULE_PENDING',updated_at=?,version=version+1
                 where id=? and owner_id=? and version=?
                   and status in ('CREATED','SCHEDULE_PENDING','SCHEDULED','SCHEDULE_FAILED','DELIVERY_FAILED','RETRYING')
                """, now, reminderId, ownerId, command.expectedVersion());
        if (eventUpdated != 1 || policyUpdated != 1 || reminderUpdated != 1) {
            throw conflict();
        }

        long updatedVersion = command.expectedVersion() + 1;
        enqueue(reminderId, "UPSERT", updatedVersion, updatedVersion + 1,
                command.startsAt().minusMinutes(command.leadMinutes()), now);
        return find(reminderId, ownerId);
    }

    private DeadlineView applyCancellation(UUID reminderId, long expectedVersion, String ownerId) {
        DeadlineView current = find(reminderId, ownerId);
        if (current.version() != expectedVersion) {
            throw conflict();
        }
        if (!canChange(current.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deadline can no longer be cancelled in status " + current.status());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = db.update("""
                update reminders set status='CANCELLED',updated_at=?,version=version+1
                 where id=? and owner_id=? and version=?
                   and status in ('CREATED','SCHEDULE_PENDING','SCHEDULED','SCHEDULE_FAILED','DELIVERY_FAILED','RETRYING')
                """, now, reminderId, ownerId, expectedVersion);
        if (updated != 1) {
            throw conflict();
        }

        long cancelledVersion = expectedVersion + 1;
        enqueue(reminderId, "DELETE", cancelledVersion, cancelledVersion, now, now);
        return find(reminderId, ownerId);
    }

    private void enqueue(UUID reminderId, String operation, long expectedVersion, long schedulerVersion,
                         OffsetDateTime dueAt, OffsetDateTime now) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reminderId", reminderId.toString(),
                    "schedulerVersion", schedulerVersion,
                    "idempotencyKey", reminderId + ":" + schedulerVersion));
            db.update("""
                    insert into schedule_outbox(
                        id,reminder_id,operation,expected_version,scheduler_version,due_at,payload,
                        status,attempts,available_at,created_at)
                    values(?,?,?,?,?,?,?,'PENDING',0,?,?)
                    """, UUID.randomUUID(), reminderId, operation, expectedVersion, schedulerVersion,
                    dueAt, payload, now, now);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot create scheduler payload", failure);
        }
    }

    private DeadlineView map(java.sql.ResultSet row) throws java.sql.SQLException {
        OffsetDateTime startsAt = row.getObject("starts_at", OffsetDateTime.class);
        int leadMinutes = row.getInt("lead_minutes");
        return new DeadlineView(
                (UUID) row.getObject("id"),
                (UUID) row.getObject("event_id"),
                (UUID) row.getObject("policy_id"),
                row.getString("title"),
                startsAt,
                leadMinutes,
                startsAt.minusMinutes(leadMinutes),
                ReminderStatus.valueOf(row.getString("status")),
                row.getString("schedule_status"),
                row.getLong("version"),
                row.getLong("event_version"),
                row.getLong("policy_version"));
    }

    private void validateFields(String title, OffsetDateTime startsAt, int leadMinutes) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (title.trim().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be at most 200 characters");
        }
        if (startsAt == null || !startsAt.isAfter(OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline must be in the future");
        }
        if (leadMinutes < 0 || leadMinutes > MAX_LEAD_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead minutes must be between 0 and 525600");
        }
        if (!startsAt.minusMinutes(leadMinutes).isAfter(OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder time must be in the future");
        }
    }

    private static boolean canChange(ReminderStatus status) {
        return status == ReminderStatus.CREATED
                || status == ReminderStatus.SCHEDULE_PENDING
                || status == ReminderStatus.SCHEDULED
                || status == ReminderStatus.SCHEDULE_FAILED
                || status == ReminderStatus.DELIVERY_FAILED
                || status == ReminderStatus.RETRYING;
    }

    private static void validateVersion(long version) {
        if (version < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version must be nonnegative");
        }
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Deadline was changed by another request");
    }

    private void requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 200) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner identity is required");
        }
    }
}
