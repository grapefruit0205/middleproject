create table travel_recommendation_consent (
  trip_id uuid not null references trips(id) on delete cascade,
  owner_id varchar(200) not null,
  status varchar(20) not null,
  version bigint not null default 0,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint travel_recommendation_consent_status_check check (status in ('PROPOSED','ACCEPTED','DECLINED')),
  constraint travel_recommendation_consent_version_check check (version >= 0),
  constraint travel_recommendation_consent_one_per_trip_owner unique (trip_id, owner_id)
);
create index travel_recommendation_consent_owner_idx on travel_recommendation_consent(owner_id, created_at);
