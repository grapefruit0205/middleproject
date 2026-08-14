package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.port.DeliveryStatusRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcDeliveryStatusRepository implements DeliveryStatusRepository {
    private final JdbcTemplate db;
    JdbcDeliveryStatusRepository(JdbcTemplate db) { this.db = db; }
    public List<Map<String,Object>> findByReminder(UUID reminderId) {
        return db.query("select status,provider_message_id,error_classification,created_at,completed_at from notification_attempt where reminder_id=? order by created_at desc",
                (resultSet, rowNumber) -> {
                    Map<String, Object> status = new LinkedHashMap<>();
                    status.put("status", resultSet.getString("status"));
                    status.put("providerMessageId", resultSet.getString("provider_message_id"));
                    status.put("errorClassification", resultSet.getString("error_classification"));
                    status.put("createdAt", resultSet.getObject("created_at"));
                    status.put("completedAt", resultSet.getObject("completed_at"));
                    return status;
                }, reminderId);
    }
}
