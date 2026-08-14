alter table reminders add column if not exists owner_id varchar(200);
create index if not exists reminders_owner_idx on reminders(owner_id, created_at);
create table if not exists mcp_audit (
  id uuid primary key,
  user_id varchar(200) not null,
  tool_name text not null,
  request_id text,
  outcome varchar(30) not null,
  reminder_id uuid references reminders(id) on delete set null,
  created_at timestamp with time zone not null
);
create index if not exists mcp_audit_user_idx on mcp_audit(user_id, created_at);
