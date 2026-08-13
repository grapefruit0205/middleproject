package com.middleproject.reminder.application;

import com.middleproject.reminder.port.SchedulerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class SchedulerOutboxService {
    private final JdbcTemplate db;
    private final SchedulerPort scheduler;
    private final TransactionTemplate tx;
    public SchedulerOutboxService(JdbcTemplate db, SchedulerPort scheduler, TransactionTemplate tx) { this.db = db; this.scheduler = scheduler; this.tx = tx; }

    public int reconcile(int limit) {
        int processed = 0;
        for (Outbox row : claim(Math.max(1, Math.min(limit, 100)))) {
            try {
                boolean current = row.operation().equals("DELETE") ? true : db.queryForObject("select count(*) from reminders where id=? and version=? and status in ('CREATED','SCHEDULE_PENDING','RETRYING','SCHEDULED')", Integer.class, row.reminderId(), row.expectedVersion()) > 0;
                if (!current) { succeed(row.id()); processed++; continue; }
                if (row.operation().equals("DELETE")) scheduler.cancel(row.reminderId(), row.schedulerVersion());
                else scheduler.register(row.reminderId(), row.schedulerVersion(), row.dueAt(), row.payload());
                if (row.operation().equals("UPSERT")) markScheduled(row); else succeed(row.id());
            } catch (RuntimeException e) { failure(row.id(), e.getMessage()); }
            processed++;
        }
        return processed;
    }
    private boolean isH2() { try (var c = db.getDataSource().getConnection()) { return c.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2"); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); } }
    private List<Outbox> claim(int limit) { return tx.execute(status -> {
        OffsetDateTime now = OffsetDateTime.now();
        db.update("update schedule_outbox set status='RETRY',claimed_at=null,available_at=? where status='CLAIMED' and claimed_at<?", now, now.minusMinutes(5));
         String claimSql = "select o.id,o.reminder_id,o.operation,o.expected_version,o.scheduler_version,o.due_at,o.payload from schedule_outbox o where (o.status='PENDING' or o.status='RETRY') and o.available_at<=? and o.attempts<10 and not exists (select 1 from schedule_outbox older where older.reminder_id=o.reminder_id and (older.created_at<o.created_at or (older.created_at=o.created_at and older.id<o.id)) and older.status <> 'SUCCEEDED') order by o.created_at,o.id limit ?";
         if (!isH2()) claimSql += " for update skip locked";
         List<Outbox> rows = db.query(claimSql, (r,n) -> new Outbox((UUID)r.getObject("id"),(UUID)r.getObject("reminder_id"),r.getString("operation"),r.getLong("expected_version"),r.getLong("scheduler_version"),r.getObject("due_at",OffsetDateTime.class),r.getString("payload")), now, limit);
        rows.forEach(r -> db.update("update schedule_outbox set status='CLAIMED',claimed_at=?,attempts=attempts+1 where id=?", now, r.id())); return rows;
    }); }
    private void markScheduled(Outbox r) { tx.executeWithoutResult(s -> { db.update("update reminders set status='SCHEDULED',updated_at=?,version=version+1 where id=? and version=? and status in ('CREATED','SCHEDULE_PENDING','RETRYING','SCHEDULED')", OffsetDateTime.now(), r.reminderId(), r.expectedVersion()); succeed(r.id()); }); }
    private void succeed(UUID id) { tx.executeWithoutResult(s -> db.update("update schedule_outbox set status='SUCCEEDED',processed_at=?,claimed_at=null where id=? and status='CLAIMED'", OffsetDateTime.now(), id)); }
    private void failure(UUID id, String message) { tx.executeWithoutResult(s -> db.update("update schedule_outbox set status=case when attempts>=10 then 'FAILED' else 'RETRY' end,available_at=?,last_error=?,claimed_at=null where id=? and status='CLAIMED'", OffsetDateTime.now().plusMinutes(1), message == null ? "scheduler failure" : message.substring(0, Math.min(1000,message.length())), id)); }
    private record Outbox(UUID id, UUID reminderId, String operation, long expectedVersion, long schedulerVersion, OffsetDateTime dueAt, String payload) {}
}
