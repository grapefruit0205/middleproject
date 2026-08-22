package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.DayPlan;
import com.middleproject.reminder.domain.DayPlanStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DayPlanRepository {
    DayPlan insert(DayPlan plan);
    List<DayPlan> findAllByOwnerAndDate(String ownerId, LocalDate planDate);
    Optional<DayPlan> findByIdForOwner(UUID id, String ownerId);
    boolean transition(UUID id, String ownerId, DayPlanStatus oldStatus, DayPlanStatus target, long version);
}
