create table schedule_outbox (
  id uuid primary key,
  reminder_id uuid not null,
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
  constraint schedule_outbox_operation_check check (operation in ('UPSERT','DELETE')),
  constraint schedule_outbox_version_check check (expected_version >= 0 and scheduler_version = expected_version + case when operation = 'UPSERT' then 1 else 0 end),
  constraint schedule_outbox_attempts_check check (attempts >= 0 and attempts <= 10)
);
create unique index schedule_outbox_reminder_version_idx on schedule_outbox(reminder_id, scheduler_version, operation);
create index schedule_outbox_pending_idx on schedule_outbox(status, available_at);
create table reminder_delivery_receipt (
  idempotency_key varchar(200) primary key,
  reminder_id uuid not null,
  scheduler_version bigint not null,
  received_at timestamp with time zone not null default current_timestamp
);
