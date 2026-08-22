package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.ScheduleItem;
import com.middleproject.reminder.domain.ScheduleItemStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleItemRepository {
    void insert(ScheduleItem item, String ownerId);
    List<ScheduleItem> findAllByPlanForOwner(UUID dayPlanId, String ownerId);
    Optional<ScheduleItem> findByIdForOwner(UUID id, String ownerId);
    boolean transition(UUID id, String ownerId, ScheduleItemStatus oldStatus, ScheduleItemStatus target, long version);
}
