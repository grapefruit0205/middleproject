create table trips (
  id uuid primary key,
  owner_id varchar(200) not null,
  departure varchar(200) not null,
  destination varchar(200) not null,
  departure_at timestamp with time zone not null,
  return_at timestamp with time zone,
  status varchar(30) not null,
  confirmation_id varchar(200),
  draft_context text,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  version bigint not null default 0,
  constraint trips_status_check check (status in ('DRAFT','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED')),
  constraint trips_time_check check (return_at is null or return_at >= departure_at)
);
create index trips_owner_idx on trips(owner_id, created_at);
create index trips_status_idx on trips(status, departure_at);

create table trip_events (
  id uuid primary key,
  trip_id uuid not null references trips(id),
  type varchar(50) not null,
  detail text,
  occurred_at timestamp with time zone not null,
  created_at timestamp with time zone not null,
  constraint trip_events_type_check check (type in ('DRAFT_CREATED','DRAFT_ANSWERED','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED','RESTARTED'))
);
create index trip_events_trip_idx on trip_events(trip_id, occurred_at);

alter table notification_policies add column if not exists trip_id uuid references trips(id);
create index if not exists notification_policies_trip_idx on notification_policies(trip_id);
alter table reminders add column if not exists trip_id uuid references trips(id);
create index if not exists reminders_trip_idx on reminders(trip_id);

create table trip_outbox (
  id uuid primary key,
  trip_id uuid not null,
  operation varchar(20) not null,
  expected_version bigint not null,
  scheduler_version bigint not null,
  due_at timestamp with time zone not null,
  payload text not null,
  status varchar(20) not null default 'PENDING',
  attempts integer not null default 0,
  available_at timestamp with time zone not null default current_timestamp,
  created_at timestamp with time zone not null default current_timestamp,
  processed_at timestamp with time zone,
  claimed_at timestamp with time zone,
  last_error varchar(1000),
  constraint trip_outbox_operation_check check (operation in ('UPSERT','DELETE')),
  constraint trip_outbox_version_check check (expected_version >= 0 and scheduler_version = expected_version + case when operation = 'UPSERT' then 1 else 0 end),
  constraint trip_outbox_attempts_check check (attempts >= 0 and attempts <= 10)
);
create unique index trip_outbox_trip_version_idx on trip_outbox(trip_id, scheduler_version, operation);
create index trip_outbox_pending_idx on trip_outbox(status, available_at);
