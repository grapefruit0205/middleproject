package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.PrivateCarRoute;

import java.util.UUID;

/** Persistence for confirmed private-car routes, one per trip. */
public interface PrivateCarRouteRepository {

    /** Inserts the confirmed route. Throws on duplicate or FK violation. */
    void insert(UUID tripId, PrivateCarRoute route, int reminderLeadMinutes);

    /** The single confirmed route for a trip, if present. */
    java.util.Optional<PrivateCarRoute> findByTrip(UUID tripId);
}
