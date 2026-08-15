package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.TripEvent;

import java.util.List;
import java.util.UUID;

public interface TripEventRepository {
    List<TripEvent> findByTrip(UUID tripId);
    void insert(TripEvent event);
}
