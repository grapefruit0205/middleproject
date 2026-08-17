-- Explicit user-saved departure locations for trip planning. No background-location history is retained.
create table origin_favorites (
  id uuid primary key,
  owner_id varchar(200) not null,
  alias varchar(100) not null,
  place_name varchar(200) not null,
  address varchar(300),
  latitude double precision not null,
  longitude double precision not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint origin_favorites_owner_alias_unique unique (owner_id, alias),
  constraint origin_favorites_latitude_check check (latitude >= 33.0 and latitude <= 39.0),
  constraint origin_favorites_longitude_check check (longitude >= 124.0 and longitude <= 132.0)
);
create index origin_favorites_owner_updated_idx on origin_favorites(owner_id, updated_at);
