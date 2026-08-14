package com.middleproject.reminder.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ReminderDeliveryService {
    public enum AcceptResult { ACCEPTED, IGNORED }
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public ReminderDeliveryService(JdbcTemplate db, com.middleproject.reminder.port.ReminderRepository ignored, ObjectMapper mapper) { this.db = db; this.mapper = mapper; }

    @Transactional
    public boolean accept(String body) { return acceptResult(body) == AcceptResult.ACCEPTED; }

    @Transactional
    public AcceptResult acceptResult(String body) {
        final Delivery m;
        try { m = mapper.readValue(body, Delivery.class); }
        catch (Exception ignored) { return AcceptResult.IGNORED; }
        if (m.reminderId == null || m.schedulerVersion == null || m.schedulerVersion < 0
                || m.idempotencyKey == null || m.idempotencyKey.isBlank() || m.idempotencyKey.length() > 200
                || !m.idempotencyKey.equals(m.reminderId + ":" + m.schedulerVersion)) return AcceptResult.IGNORED;
        String receiptInsert = isH2()
                ? "insert into reminder_delivery_receipt(idempotency_key,reminder_id,scheduler_version) select ?,?,? where not exists (select 1 from reminder_delivery_receipt where idempotency_key=?)"
                : "insert into reminder_delivery_receipt(idempotency_key,reminder_id,scheduler_version) values(?,?,?) on conflict (idempotency_key) do nothing";
        int inserted = isH2()
                ? db.update(receiptInsert, m.idempotencyKey, m.reminderId, m.schedulerVersion, m.idempotencyKey)
                : db.update(receiptInsert, m.idempotencyKey, m.reminderId, m.schedulerVersion);
        if (inserted == 0) return AcceptResult.IGNORED;
        int updated = db.update("update reminders set status='DISPATCHED',updated_at=?,version=version+1 where id=? and version=? and status='SCHEDULED'",
                java.time.OffsetDateTime.now(), m.reminderId, m.schedulerVersion);
        if (updated > 0) return AcceptResult.ACCEPTED;
        db.update("delete from reminder_delivery_receipt where idempotency_key=?", m.idempotencyKey);
        return AcceptResult.IGNORED;
    }
    private boolean isH2() {
        try (var connection = db.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot identify database", e);
        }
    }

    private static class Delivery { public UUID reminderId; public Long schedulerVersion; public String idempotencyKey; }
}
