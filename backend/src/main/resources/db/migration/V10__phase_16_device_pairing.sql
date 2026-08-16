-- Phase 16: Android companion device pairing.
-- Only salted slow hashes of pairing codes and SHA-256 hashes of device bearer tokens
-- are stored. The opaque device bearer token is never persisted. The FCM registration
-- token (a real value required by the Phase 17 server-side sender, distinct from the
-- device bearer token and from Firebase server credentials) is stored together with its
-- SHA-256 hash; the hash keeps uniqueness/querying and never leaks in logs or audit.

create table device_pairing_codes (
  code_hash varchar(64) primary key,
  salt varchar(64) not null,
  active_slot integer,
  status varchar(20) not null,
  issued_at timestamp with time zone not null,
  expires_at timestamp with time zone not null,
  consumed_at timestamp with time zone,
  created_at timestamp with time zone not null default current_timestamp,
  constraint device_pairing_codes_status_check check (status in ('ACTIVE', 'CONSUMED', 'EXPIRED')),
  constraint device_pairing_codes_active_slot_check check (active_slot is null or active_slot = 1)
);

-- At most one active pairing code at a time: ACTIVE rows carry active_slot=1 and the
-- unique index rejects a second one; CONSUMED/EXPIRED rows release the slot to NULL,
-- and the unique index permits multiple NULLs on both H2 and PostgreSQL. Because the
-- index is single-column, PostgreSQL's ON CONFLICT (active_slot) targets it directly.
create unique index device_pairing_codes_single_active_idx on device_pairing_codes(active_slot);
create index device_pairing_codes_expiry_idx on device_pairing_codes(status, expires_at);

create table devices (
  id uuid primary key,
  owner_id varchar(200) not null,
  installation_id varchar(200) not null,
  label varchar(200) not null,
  token_hash varchar(64) not null,
  status varchar(20) not null,
  expires_at timestamp with time zone not null,
  created_at timestamp with time zone not null,
  revoked_at timestamp with time zone,
  constraint devices_status_check check (status in ('ACTIVE', 'REVOKED')),
  constraint devices_lifetime_check check (expires_at > created_at),
  constraint devices_one_device_per_installation unique (owner_id, installation_id),
  constraint devices_token_hash_unique unique (token_hash)
);
create index devices_owner_idx on devices(owner_id, created_at);

-- The FCM registration token is a real value the Phase 17 sender needs for delivery;
-- it is not the opaque device bearer token (whose hash lives in devices.token_hash) and
-- not a Firebase server credential. The hash column keeps uniqueness/querying and never
-- leaks in logs or audit; neither column ever holds a Firebase server credential.
create table device_fcm_registration (
  device_id uuid primary key references devices(id) on delete cascade,
  registration_token varchar(4096) not null,
  registration_token_hash varchar(64) not null,
  registered_at timestamp with time zone not null,
  constraint device_fcm_registration_token_hash_unique unique (registration_token_hash)
);
