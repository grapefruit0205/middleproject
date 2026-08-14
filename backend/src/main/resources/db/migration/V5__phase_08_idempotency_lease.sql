alter table idempotency_record add column if not exists lease_until timestamp with time zone;
alter table idempotency_record add column if not exists last_claim_at timestamp with time zone;
update idempotency_record
set lease_until = coalesce(lease_until, case when status = 'IN_PROGRESS' then created_at else null end),
    last_claim_at = coalesce(last_claim_at, created_at);
alter table idempotency_record alter column lease_until set default null;
alter table idempotency_record alter column last_claim_at set default null;
create index if not exists idempotency_lease_idx on idempotency_record(status, lease_until);
alter table idempotency_record add column if not exists claim_token varchar(36);
update idempotency_record set claim_token = coalesce(claim_token, 'legacy') where status = 'IN_PROGRESS';
create index if not exists idempotency_claim_token_idx on idempotency_record(scope, idempotency_key, claim_token);
