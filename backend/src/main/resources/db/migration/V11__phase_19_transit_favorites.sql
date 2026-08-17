-- Phase 19: owner-scoped subway/bus favorites shared by ChatGPT MCP and Android widgets.
create table transit_favorites (
  id uuid primary key,
  owner_id varchar(200) not null,
  alias varchar(100) not null,
  mode varchar(20) not null,
  station_name varchar(200),
  city_code integer,
  node_id varchar(200),
  stop_name varchar(200),
  route_no varchar(100),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint transit_favorites_mode_check check (mode in ('SUBWAY', 'BUS')),
  constraint transit_favorites_payload_check check (
    (mode = 'SUBWAY' and station_name is not null and city_code is null and node_id is null)
    or (mode = 'BUS' and station_name is null and city_code is not null and node_id is not null)
  ),
  constraint transit_favorites_owner_alias_unique unique (owner_id, alias)
);
create index transit_favorites_owner_updated_idx on transit_favorites(owner_id, updated_at);
