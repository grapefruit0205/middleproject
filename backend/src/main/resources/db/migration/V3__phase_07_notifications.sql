create table notification_attempt (
  id uuid primary key,
  reminder_id uuid not null references reminders(id),
  correlation_id uuid not null unique,
  delivery_key varchar(200) not null,
  active_delivery_key varchar(200) unique,
  channel varchar(30),
  recipient varchar(500),
  status varchar(30) not null,
  provider_message_id varchar(500),
  error_classification varchar(50),
  error_message varchar(2000),
  created_at timestamp with time zone not null,
  completed_at timestamp with time zone
);
create index notification_attempt_reminder_idx on notification_attempt(reminder_id, created_at);
