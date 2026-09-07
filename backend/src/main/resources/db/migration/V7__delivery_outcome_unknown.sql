alter table reminders drop constraint if exists reminders_status_check;

alter table reminders add constraint reminders_status_check check (
  status in (
    'CREATED','SCHEDULE_PENDING','SCHEDULED','DISPATCHED','DELIVERED','ACKNOWLEDGED',
    'SCHEDULE_FAILED','DELIVERY_FAILED','DELIVERY_UNKNOWN','RETRYING','CANCELLED'
  )
);
