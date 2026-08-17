package com.middleproject.reminder.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Explicitly saved origin aliases only. This service never persists background device location. */
@Service
public class OriginFavoriteService {
    private final JdbcTemplate db;
    private final IdempotencyService idempotency;

    public OriginFavoriteService(JdbcTemplate db, IdempotencyService idempotency) {
        this.db = db;
        this.idempotency = idempotency;
    }

    public record Favorite(UUID id, String alias, String placeName, String address,
                           double latitude, double longitude, OffsetDateTime updatedAt) {}

    @Transactional(readOnly = true)
    public List<Favorite> list(String ownerId) {
        requireOwner(ownerId);
        return db.query("""
                select id, alias, place_name, address, latitude, longitude, updated_at
                from origin_favorites where owner_id=? order by updated_at desc, alias
                """, (rs, row) -> new Favorite(
                rs.getObject("id", UUID.class), rs.getString("alias"), rs.getString("place_name"),
                rs.getString("address"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getObject("updated_at", OffsetDateTime.class)), ownerId);
    }

    public Favorite save(String ownerId, String alias, String placeName, String address,
                         double latitude, double longitude, String key) {
        requireOwner(ownerId);
        requireText("alias", alias, 100);
        requireText("placeName", placeName, 200);
        if (address != null && address.length() > 300) throw new IllegalArgumentException("address must be at most 300 characters");
        requireCoordinates(latitude, longitude);
        return idempotency.execute("origin-favorite:" + ownerId, key,
                new SavePayload(alias.trim(), placeName.trim(), clean(address), latitude, longitude), Favorite.class,
                () -> upsert(ownerId, alias.trim(), placeName.trim(), clean(address), latitude, longitude));
    }

    public Favorite delete(String ownerId, UUID favoriteId, String key) {
        requireOwner(ownerId);
        if (favoriteId == null) throw new IllegalArgumentException("favorite id is required");
        return idempotency.execute("origin-favorite:" + ownerId, key, new DeletePayload(favoriteId), Favorite.class,
                () -> {
                    Favorite found = find(ownerId, favoriteId);
                    if (db.update("delete from origin_favorites where id=? and owner_id=?", favoriteId, ownerId) != 1) {
                        throw new IllegalStateException("origin favorite was not deleted");
                    }
                    return found;
                });
    }

    @Transactional
    Favorite upsert(String ownerId, String alias, String placeName, String address, double latitude, double longitude) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> existing = db.query("select id from origin_favorites where owner_id=? and alias=?",
                (rs, row) -> rs.getObject(1, UUID.class), ownerId, alias);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        if (existing.isEmpty()) {
            db.update("""
                    insert into origin_favorites
                    (id, owner_id, alias, place_name, address, latitude, longitude, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, ownerId, alias, placeName, address, latitude, longitude, now, now);
        } else {
            db.update("""
                    update origin_favorites set place_name=?, address=?, latitude=?, longitude=?, updated_at=?
                    where id=? and owner_id=?
                    """, placeName, address, latitude, longitude, now, id, ownerId);
        }
        return find(ownerId, id);
    }

    private Favorite find(String ownerId, UUID id) {
        List<Favorite> results = db.query("""
                select id, alias, place_name, address, latitude, longitude, updated_at
                from origin_favorites where owner_id=? and id=?
                """, (rs, row) -> new Favorite(
                rs.getObject("id", UUID.class), rs.getString("alias"), rs.getString("place_name"),
                rs.getString("address"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getObject("updated_at", OffsetDateTime.class)), ownerId, id);
        if (results.isEmpty()) throw new IllegalArgumentException("origin favorite was not found");
        return results.getFirst();
    }

    private static void requireOwner(String value) { requireText("owner", value, 200); }

    private static void requireText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be nonblank and at most " + maximumLength + " characters");
        }
    }

    private static void requireCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            throw new IllegalArgumentException("coordinates must be within South Korea");
        }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record SavePayload(String alias, String placeName, String address, double latitude, double longitude) {}
    private record DeletePayload(UUID id) {}
}
