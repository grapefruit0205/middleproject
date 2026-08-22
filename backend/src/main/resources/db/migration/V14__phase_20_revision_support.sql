alter table day_plans add column if not exists draft_json text;
alter table reminders add column if not exists schedule_item_id uuid references schedule_items(id) on delete set null;
create index if not exists reminders_schedule_item_idx on reminders(schedule_item_id);
