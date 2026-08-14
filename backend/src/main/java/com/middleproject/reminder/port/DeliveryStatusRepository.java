package com.middleproject.reminder.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DeliveryStatusRepository {
    List<Map<String,Object>> findByReminder(UUID reminderId);
}
