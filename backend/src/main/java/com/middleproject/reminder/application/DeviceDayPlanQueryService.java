package com.middleproject.reminder.application;

import com.middleproject.reminder.domain.GeoPoint;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read projection used by the paired Android companion; every query is owner scoped. */
@Service
public class DeviceDayPlanQueryService {
    private final JdbcTemplate db;

    public DeviceDayPlanQueryService(JdbcTemplate db) { this.db = db; }

    public List<DayPlanView> findAll(String ownerId, LocalDate date) {
        return db.query("select id,plan_date,timezone,status,version from day_plans where owner_id=? and plan_date=? order by id",
                (rs, n) -> mapPlan(rs, ownerId), ownerId, date);
    }

    public Optional<DayPlanView> find(String ownerId, UUID planId) {
        try {
            return Optional.of(db.queryForObject("select id,plan_date,timezone,status,version from day_plans where id=? and owner_id=?",
                    (rs, n) -> mapPlan(rs, ownerId), planId, ownerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private DayPlanView mapPlan(java.sql.ResultSet rs, String ownerId) throws java.sql.SQLException {
        UUID planId = (UUID) rs.getObject("id");
        List<ItemView> items = db.query(
                "select s.id,s.sequence,s.title,s.time_type,s.starts_at,s.ends_at,s.duration_minutes,s.place_name,s.address,s.latitude,s.longitude,s.status,s.version,"
                        + "e.starts_at as notification_event_at,p.lead_minutes,r.status as reminder_status,r.version as reminder_version "
                        + "from schedule_items s left join reminders r on r.schedule_item_id=s.id and r.owner_id=? "
                        + "left join events e on e.id=r.event_id left join notification_policies p on p.id=r.policy_id "
                        + "where s.day_plan_id=? order by s.sequence",
                (item, n) -> mapItem(item), ownerId, planId);
        List<TravelLegView> legs = db.query(
                "select id,from_item_id,to_item_id,mode,duration_minutes,buffer_minutes,departure_at,arrival_at,provider,source,fetched_at,sequence,version from travel_legs where day_plan_id=? order by sequence",
                (leg, n) -> new TravelLegView((UUID) leg.getObject("id"), (UUID) leg.getObject("from_item_id"),
                        (UUID) leg.getObject("to_item_id"), leg.getString("mode"), leg.getInt("duration_minutes"),
                        leg.getInt("buffer_minutes"), leg.getObject("departure_at", OffsetDateTime.class),
                        leg.getObject("arrival_at", OffsetDateTime.class), leg.getString("provider"),
                        leg.getString("source"), leg.getObject("fetched_at", java.time.Instant.class),
                        leg.getInt("sequence"), leg.getLong("version")), planId);
        return new DayPlanView(planId, rs.getObject("plan_date", LocalDate.class), rs.getString("timezone"),
                rs.getString("status"), rs.getLong("version"), items, legs);
    }

    private ItemView mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        Double latitude = rs.getObject("latitude", Double.class);
        Double longitude = rs.getObject("longitude", Double.class);
        GeoPoint coordinates = latitude == null || longitude == null ? null : new GeoPoint(latitude, longitude);
        OffsetDateTime eventAt = rs.getObject("notification_event_at", OffsetDateTime.class);
        Integer lead = rs.getObject("lead_minutes", Integer.class);
        OffsetDateTime notificationAt = eventAt == null || lead == null ? null : eventAt.minusMinutes(lead);
        return new ItemView((UUID) rs.getObject("id"), rs.getInt("sequence"), rs.getString("title"),
                rs.getString("time_type"), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class), rs.getInt("duration_minutes"), rs.getString("place_name"),
                rs.getString("address"), coordinates, rs.getString("status"), rs.getLong("version"),
                notificationAt, rs.getString("reminder_status"), rs.getObject("reminder_version", Long.class));
    }

    public record DayPlanView(UUID id, LocalDate planDate, String timezone, String status, long version,
                              List<ItemView> items, List<TravelLegView> travelLegs) {
        public DayPlanView { items = List.copyOf(items); travelLegs = List.copyOf(travelLegs); }
    }

    public record ItemView(UUID id, int sequence, String title, String timeType, OffsetDateTime startsAt,
                           OffsetDateTime endsAt, int durationMinutes, String placeName, String address,
                           GeoPoint coordinates, String status, long version, OffsetDateTime notificationAt,
                           String reminderStatus, Long reminderVersion) {}

    public record TravelLegView(UUID id, UUID fromItemId, UUID toItemId, String mode, int durationMinutes,
                                int bufferMinutes, OffsetDateTime departureAt, OffsetDateTime arrivalAt,
                                String provider, String source, java.time.Instant fetchedAt, int sequence,
                                long version) {}
}
