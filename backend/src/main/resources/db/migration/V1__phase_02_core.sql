create table events (
  id uuid primary key, title varchar(200) not null, starts_at timestamp with time zone not null, ends_at timestamp with time zone,
  created_at timestamp with time zone not null, updated_at timestamp with time zone not null,
  version bigint not null default 0, constraint events_time_check check (ends_at is null or ends_at >= starts_at)
);
create table notification_policies (
  id uuid primary key, channel varchar(30) not null, lead_minutes integer not null,
  created_at timestamp with time zone not null, updated_at timestamp with time zone not null, version bigint not null default 0,
  constraint notification_policy_lead_check check (lead_minutes >= 0)
);
create table reminders (
  id uuid primary key, event_id uuid not null references events(id), policy_id uuid not null references notification_policies(id),
  status varchar(30) not null, created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null, version bigint not null default 0,
  constraint reminders_status_check check (status in ('CREATED','SCHEDULE_PENDING','SCHEDULED','DISPATCHED','DELIVERED','ACKNOWLEDGED','SCHEDULE_FAILED','DELIVERY_FAILED','RETRYING','CANCELLED'))
);
create table idempotency_record (
  scope varchar(200) not null, idempotency_key varchar(200) not null, request_hash varchar(64) not null,
  response_status integer, response_body text, created_at timestamp with time zone not null, completed_at timestamp with time zone,
  primary key (scope, idempotency_key)
);
create index reminders_event_idx on reminders(event_id);