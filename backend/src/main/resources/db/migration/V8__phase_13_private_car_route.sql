create table private_car_routes (
  trip_id uuid primary key references trips(id),
  stable_id varchar(64) not null,
  origin varchar(200) not null,
  destination varchar(200) not null,
  departure_at timestamp with time zone not null,
  origin_lat double precision not null,
  origin_lng double precision not null,
  destination_lat double precision not null,
  destination_lng double precision not null,
  distance_meters integer not null,
  base_duration_minutes integer not null,
  traffic_duration_minutes integer not null,
  toll_amount integer not null,
  provider varchar(100) not null,
  source varchar(100) not null,
  recommended_departure_at timestamp with time zone not null,
  reminder_lead_minutes integer not null,
  preview_fetched_at timestamp with time zone not null,
  preview_expires_at timestamp with time zone not null,
  created_at timestamp with time zone not null,
  constraint private_car_routes_distance_check check (distance_meters >= 0),
  constraint private_car_routes_durations_check check (base_duration_minutes >= 0 and traffic_duration_minutes >= base_duration_minutes),
  constraint private_car_routes_toll_check check (toll_amount >= 0),
  constraint private_car_routes_lead_check check (reminder_lead_minutes between 0 and 1440),
  constraint private_car_routes_provenance_check check (preview_expires_at > preview_fetched_at)
);
create unique index private_car_routes_stable_idx on private_car_routes(trip_id, stable_id);
create index private_car_routes_expiry_idx on private_car_routes(preview_expires_at);

alter table trip_events drop constraint trip_events_type_check;
alter table trip_events add constraint trip_events_type_check check (
  type in ('DRAFT_CREATED','DRAFT_ANSWERED','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED','RESTARTED','PRIVATE_CAR_ROUTE_CONFIRMED')
);
