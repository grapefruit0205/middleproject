create table day_plans (
  id uuid primary key,
  owner_id varchar(200) not null,
  plan_date date not null,
  timezone varchar(80) not null default 'Asia/Seoul',
  status varchar(30) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  version bigint not null default 0,
  constraint day_plans_status_check check (status in ('DRAFT','PROPOSED','CONFIRMED','ACTIVE','COMPLETED','CANCELLED')),
  constraint day_plans_version_check check (version >= 0),
  constraint day_plans_timezone_check check (length(trim(timezone)) > 0)
);
create unique index day_plans_owner_date_idx on day_plans(owner_id, plan_date);
create index day_plans_status_idx on day_plans(owner_id, status, plan_date);

create table schedule_items (
  id uuid primary key,
  day_plan_id uuid not null references day_plans(id) on delete cascade,
  title varchar(300) not null,
  time_type varchar(30) not null,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  duration_minutes integer not null default 0,
  place_name varchar(300) not null,
  address varchar(500),
  latitude double precision,
  longitude double precision,
  sequence integer not null,
  status varchar(30) not null default 'PLANNED',
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  version bigint not null default 0,
  constraint schedule_items_time_type_check check (time_type in ('FIXED_START','ARRIVAL_DEADLINE','FLEXIBLE')),
  constraint schedule_items_status_check check (status in ('PLANNED','ACTIVE','COMPLETED','CANCELLED')),
  constraint schedule_items_duration_check check (duration_minutes >= 0),
  constraint schedule_items_sequence_check check (sequence >= 0),
  constraint schedule_items_version_check check (version >= 0),
  constraint schedule_items_time_check check (ends_at is null or starts_at is null or ends_at >= starts_at),
  constraint schedule_items_coordinates_check check ((latitude is null and longitude is null) or (latitude between -90 and 90 and longitude between -180 and 180)),
  constraint schedule_items_fixed_time_check check (time_type = 'FLEXIBLE' or starts_at is not null)
);
create unique index schedule_items_plan_sequence_idx on schedule_items(day_plan_id, sequence);
create index schedule_items_plan_status_idx on schedule_items(day_plan_id, status, sequence);

create table travel_legs (
  id uuid primary key,
  day_plan_id uuid not null references day_plans(id) on delete cascade,
  from_item_id uuid references schedule_items(id) on delete cascade,
  to_item_id uuid not null references schedule_items(id) on delete cascade,
  mode varchar(40) not null,
  duration_minutes integer not null,
  buffer_minutes integer not null default 0,
  departure_at timestamp with time zone not null,
  arrival_at timestamp with time zone not null,
  provider varchar(120) not null,
  source varchar(500) not null,
  fetched_at timestamp with time zone not null,
  sequence integer not null,
  version bigint not null default 0,
  constraint travel_legs_duration_check check (duration_minutes >= 0),
  constraint travel_legs_buffer_check check (buffer_minutes >= 0),
  constraint travel_legs_time_check check (arrival_at >= departure_at),
  constraint travel_legs_sequence_check check (sequence >= 0),
  constraint travel_legs_version_check check (version >= 0)
);
create unique index travel_legs_plan_sequence_idx on travel_legs(day_plan_id, sequence);
create index travel_legs_plan_idx on travel_legs(day_plan_id, sequence);
