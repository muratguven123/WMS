$ErrorActionPreference = "Stop"

Write-Host "WMS PostgreSQL databases on localhost:5432"
Write-Host "============================================"
Write-Host ""

$dbs = @(
    "wms_core_db",
    "wms_localization_db",
    "wms_finance_db",
    "wms_billing_db",
    "wms_integration_db",
    "wms_inbound_db",
    "wms_inventory_db",
    "wms_outbound_db",
    "keycloak_db"
)

Write-Host ("{0,-24} {1}" -f "Database", "Tables")
Write-Host ("{0,-24} {1}" -f "--------", "------")

foreach ($db in $dbs) {
    $count = docker run --rm -e PGPASSWORD=postgres --add-host=host.docker.internal:host-gateway postgres:16-alpine `
        psql -h host.docker.internal -p 5432 -U postgres -d $db -tAc `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type='BASE TABLE' AND table_name NOT LIKE 'flyway%';" 2>$null
    if ($LASTEXITCODE -ne 0) { $count = "MISSING" }
    Write-Host ("{0,-24} {1}" -f $db, $count)
}

Write-Host ""
Write-Host "Legacy wrong names (should be gone): wms-inventory-db, wms-inbound-db, wms-outbond-db"
Write-Host ""
Write-Host "pgAdmin (preconfigured): http://localhost:5050"
Write-Host "  Email: admin@wms.com   Password: admin"
Write-Host ""
Write-Host "Desktop pgAdmin: localhost:5432, user=postgres, password=postgres"
Write-Host ""
Write-Host "Inventory/Billing path in pgAdmin:"
Write-Host "  wms_inventory_db -> Schemas -> public -> Tables"
Write-Host "  wms_billing_db   -> Schemas -> public -> Tables"
