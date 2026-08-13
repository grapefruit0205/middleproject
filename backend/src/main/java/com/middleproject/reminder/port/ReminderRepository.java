package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository {
    List<Reminder> findAll();
    Optional<Reminder> findById(UUID id);
    Reminder insert(UUID id, UUID eventId, UUID policyId);
    boolean update(UUID id, UUID eventId, UUID policyId, long version);
    boolean transition(UUID id, ReminderStatus oldStatus, ReminderStatus target, long version);
    boolean delete(UUID id, long version);
}
