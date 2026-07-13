#!/bin/sh
set -e

PGHOST="${PGHOST:-host.docker.internal}"
PGUSER="${PGUSER:-postgres}"
PGPASSWORD="${PGPASSWORD:-postgres}"
export PGPASSWORD

psql_cmd() {
  psql -h "$PGHOST" -U "$PGUSER" "$@"
}

echo "Waiting for PostgreSQL at ${PGHOST} ..."
attempt=0
until psql_cmd -tAc "SELECT 1" postgres >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "ERROR: PostgreSQL is not reachable at ${PGHOST}:5432"
    echo "Start your local PostgreSQL service, then run: docker compose up wms-db-init"
    exit 1
  fi
  sleep 2
done
echo "PostgreSQL is ready."

DATABASES="
wms_core_db
wms_localization_db
wms_finance_db
wms_billing_db
wms_integration_db
wms_inbound_db
wms_inventory_db
wms_outbound_db
keycloak_db
"

for db in $DATABASES; do
  exists=$(psql_cmd -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" postgres || true)
  if [ "$exists" != "1" ]; then
    echo "Creating database: ${db}"
    psql_cmd -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"${db}\";" postgres
  else
    echo "Database already exists: ${db}"
  fi
done

echo "All WMS databases are ready."

# Remove legacy databases with wrong names (hyphens / typos). They are empty and confuse pgAdmin.
LEGACY_DATABASES="
wms-inventory-db
wms-inbound-db
wms-outbond-db
"

for db in $LEGACY_DATABASES; do
  exists=$(psql_cmd -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" postgres || true)
  if [ "$exists" = "1" ]; then
    echo "Dropping legacy empty database: ${db}"
    psql_cmd -v ON_ERROR_STOP=1 -c "
      SELECT pg_terminate_backend(pid)
      FROM pg_stat_activity
      WHERE datname = '${db}' AND pid <> pg_backend_pid();
    " postgres
    psql_cmd -v ON_ERROR_STOP=1 -c "DROP DATABASE \"${db}\";" postgres
  fi
done

echo ""
echo "Open in pgAdmin (use underscore names):"
echo "  wms_core_db, wms_localization_db, wms_finance_db, wms_billing_db,"
echo "  wms_integration_db, wms_inbound_db, wms_inventory_db, wms_outbound_db"
