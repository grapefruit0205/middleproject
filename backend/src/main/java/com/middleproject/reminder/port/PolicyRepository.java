package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.NotificationPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository {
    List<NotificationPolicy> findAll();
    Optional<NotificationPolicy> findById(UUID id);
    NotificationPolicy insert(UUID id, String channel, int leadMinutes);
    boolean update(UUID id, String channel, int leadMinutes, long version);
    boolean delete(UUID id, long version);
}
