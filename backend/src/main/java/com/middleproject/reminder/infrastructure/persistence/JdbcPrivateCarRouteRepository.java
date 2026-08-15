package com.middleproject.reminder.infrastructure.persistence;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.PrivateCarRoute;
import com.middleproject.reminder.port.PrivateCarRouteRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPrivateCarRouteRepository implements PrivateCarRouteRepository {
    private final JdbcTemplate db;
    private final Clock clock;

    JdbcPrivateCarRouteRepository(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }

    @Override
    public void insert(UUID tripId, PrivateCarRoute route, int reminderLeadMinutes) {
        db.update("insert into private_car_routes(trip_id,stable_id,origin,destination,departure_at,origin_lat,origin_lng,destination_lat,destination_lng," +
                        "distance_meters,base_duration_minutes,traffic_duration_minutes,toll_amount,provider,source,recommended_departure_at," +
                        "reminder_lead_minutes,preview_fetched_at,preview_expires_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tripId, route.stableId(), route.origin(), route.destination(), route.departureAt(),
                route.originPoint().latitude(), route.originPoint().longitude(),
                route.destinationPoint().latitude(), route.destinationPoint().longitude(),
                route.distanceMeters(), route.baseDurationMinutes(), route.trafficDurationMinutes(), route.tollAmount(),
                route.provider(), route.source(), route.recommendedDepartureAt(), reminderLeadMinutes,
                route.fetchedAt(), route.expiresAt(), OffsetDateTime.now(clock));
    }

    @Override
    public Optional<PrivateCarRoute> findByTrip(UUID tripId) {
        try {
            return Optional.of(db.queryForObject("select stable_id,origin,destination,departure_at,origin_lat,origin_lng," +
                    "destination_lat,destination_lng,distance_meters,base_duration_minutes,traffic_duration_minutes," +
                    "toll_amount,provider,source,preview_fetched_at,preview_expires_at from private_car_routes where trip_id=?",
                    (r, n) -> map(r), tripId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private PrivateCarRoute map(ResultSet r) throws SQLException {
        return new PrivateCarRoute(r.getString("stable_id"), r.getString("origin"), r.getString("destination"),
                r.getObject("departure_at", OffsetDateTime.class),
                new GeoPoint(r.getDouble("origin_lat"), r.getDouble("origin_lng")),
                new GeoPoint(r.getDouble("destination_lat"), r.getDouble("destination_lng")),
                r.getInt("distance_meters"), r.getInt("base_duration_minutes"), r.getInt("traffic_duration_minutes"),
                r.getInt("toll_amount"), r.getString("provider"), r.getString("source"),
                r.getObject("preview_fetched_at", OffsetDateTime.class),
                r.getObject("preview_expires_at", OffsetDateTime.class));
    }
}
