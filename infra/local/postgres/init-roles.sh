#!/bin/sh
set -eu

for required in DB_MIGRATION_USERNAME DB_MIGRATION_PASSWORD DB_APP_USERNAME DB_APP_PASSWORD; do
  eval "value=\${$required:-}"
  if [ -z "$value" ]; then
    echo "ERROR: $required is required" >&2
    exit 1
  fi
done

if [ "$POSTGRES_USER" = "$DB_MIGRATION_USERNAME" ] \
  || [ "$POSTGRES_USER" = "$DB_APP_USERNAME" ] \
  || [ "$DB_MIGRATION_USERNAME" = "$DB_APP_USERNAME" ]; then
  echo "ERROR: administrator, migration, and runtime roles must be distinct" >&2
  exit 1
fi

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
  --set=migration_user="$DB_MIGRATION_USERNAME" --set=migration_password="$DB_MIGRATION_PASSWORD" \
  --set=app_user="$DB_APP_USERNAME" --set=app_password="$DB_APP_PASSWORD" --set=db_name="$POSTGRES_DB" <<'SQL'
select format('create role %I login password %L nosuperuser nocreatedb nocreaterole noinherit', :'migration_user', :'migration_password') \gexec
select format('create role %I login password %L nosuperuser nocreatedb nocreaterole noinherit', :'app_user', :'app_password') \gexec
select format('grant connect on database %I to %I', :'db_name', :'migration_user') \gexec
select format('grant connect on database %I to %I', :'db_name', :'app_user') \gexec
select format('grant usage, create on schema public to %I', :'migration_user') \gexec
select format('grant usage on schema public to %I', :'app_user') \gexec
select format('revoke create on schema public from %I', :'app_user') \gexec
select format('alter default privileges for role %I in schema public grant select, insert, update, delete on tables to %I', :'migration_user', :'app_user') \gexec
select format('alter default privileges for role %I in schema public grant usage, select, update on sequences to %I', :'migration_user', :'app_user') \gexec
SQL
