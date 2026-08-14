alter table idempotency_record add column if not exists status varchar(20) not null default 'IN_PROGRESS';
alter table idempotency_record add column if not exists attempts integer not null default 0;
alter table idempotency_record add column if not exists last_error varchar(1000);
update idempotency_record set status = case when response_status is not null then 'COMPLETED' else status end;
alter table idempotency_record add constraint idempotency_status_check check (status in ('IN_PROGRESS','COMPLETED','FAILED'));

create index if not exists schedule_outbox_failed_idx on schedule_outbox(status, attempts, available_at);
create index if not exists notification_attempt_retry_idx on notification_attempt(reminder_id, status, created_at);
