package com.middleproject.reminder.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TransitFavoriteService {
    private final JdbcTemplate db;
    private final IdempotencyService idempotency;

    public TransitFavoriteService(JdbcTemplate db, IdempotencyService idempotency) {
        this.db = db;
        this.idempotency = idempotency;
    }

    public record Favorite(UUID id, String alias, String mode, String stationName,
                           Integer cityCode, String nodeId, String stopName, String routeNo,
                           OffsetDateTime updatedAt) {}

    @Transactional(readOnly = true)
    public List<Favorite> list(String ownerId) {
        return db.query("""
                select id, alias, mode, station_name, city_code, node_id, stop_name, route_no, updated_at
                from transit_favorites where owner_id=? order by updated_at desc, alias
                """, (rs, row) -> new Favorite(
                rs.getObject("id", UUID.class), rs.getString("alias"), rs.getString("mode"),
                rs.getString("station_name"), (Integer) rs.getObject("city_code"), rs.getString("node_id"),
                rs.getString("stop_name"), rs.getString("route_no"), rs.getObject("updated_at", OffsetDateTime.class)),
                ownerId);
    }

    public Favorite saveSubway(String ownerId, String alias, String stationName, String key) {
        validate(ownerId, alias, stationName);
        return idempotency.execute("transit-favorite:" + ownerId, key,
                new SubwayPayload(alias.trim(), stationName.trim()), Favorite.class,
                () -> upsert(ownerId, alias.trim(), "SUBWAY", stationName.trim(), null, null, null, null));
    }

    public Favorite saveBus(String ownerId, String alias, int cityCode, String nodeId,
                            String stopName, String routeNo, String key) {
        validate(ownerId, alias, nodeId);
        if (cityCode < 1) throw new IllegalArgumentException("cityCode must be positive");
        return idempotency.execute("transit-favorite:" + ownerId, key,
                new BusPayload(alias.trim(), cityCode, nodeId.trim(), clean(stopName), clean(routeNo)), Favorite.class,
                () -> upsert(ownerId, alias.trim(), "BUS", null, cityCode, nodeId.trim(), clean(stopName), clean(routeNo)));
    }

    @Transactional
    Favorite upsert(String ownerId, String alias, String mode, String stationName,
                    Integer cityCode, String nodeId, String stopName, String routeNo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> existing = db.query("select id from transit_favorites where owner_id=? and alias=?",
                (rs, row) -> rs.getObject(1, UUID.class), ownerId, alias);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        if (existing.isEmpty()) {
            db.update("""
                    insert into transit_favorites
                    (id,owner_id,alias,mode,station_name,city_code,node_id,stop_name,route_no,created_at,updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?)
                    """, id, ownerId, alias, mode, stationName, cityCode, nodeId, stopName, routeNo, now, now);
        } else {
            db.update("""
                    update transit_favorites set mode=?,station_name=?,city_code=?,node_id=?,stop_name=?,route_no=?,updated_at=?
                    where id=? and owner_id=?
                    """, mode, stationName, cityCode, nodeId, stopName, routeNo, now, id, ownerId);
        }
        return list(ownerId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static void validate(String ownerId, String alias, String value) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 200) throw new IllegalArgumentException("invalid owner");
        if (alias == null || alias.isBlank() || alias.length() > 100) throw new IllegalArgumentException("alias must be 1-100 characters");
        if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException("favorite target must be 1-200 characters");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SubwayPayload(String alias, String stationName) {}
    private record BusPayload(String alias, int cityCode, String nodeId, String stopName, String routeNo) {}
}
