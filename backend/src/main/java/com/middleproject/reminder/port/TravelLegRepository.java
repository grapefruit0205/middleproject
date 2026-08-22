package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.TravelLeg;

import java.util.List;
import java.util.UUID;

public interface TravelLegRepository {
    void insert(TravelLeg leg, String ownerId);
    List<TravelLeg> findAllByPlanForOwner(UUID dayPlanId, String ownerId);
}
