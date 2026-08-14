package com.middleproject.reminder.application;

import com.middleproject.reminder.port.DeliveryStatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpReminderQueryService {
    private final DeliveryStatusRepository deliveryStatus;

    public McpReminderQueryService(DeliveryStatusRepository deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public List<Map<String, Object>> deliveryStatus(UUID reminderId) { return deliveryStatus.findByReminder(reminderId); }
}
