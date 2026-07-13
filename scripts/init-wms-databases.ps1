param(
    [switch]$RestartServices
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "WMS database initialization"
Write-Host "==========================="
Write-Host "pgAdmin / DBeaver: localhost:5432  user=postgres  password=postgres"
Write-Host "All microservices write to this same local PostgreSQL instance."
Write-Host ""

Write-Host "Creating databases (if missing) ..."
Push-Location $root
docker compose up wms-db-init
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    exit $LASTEXITCODE
}
Pop-Location

if ($RestartServices) {
    Write-Host "Starting all services to run Flyway migrations ..."
    Push-Location $root
    docker compose up -d
    Pop-Location
} else {
    Write-Host ""
    Write-Host "Databases ready. Tables are created when microservices start."
    Write-Host "Run:  docker compose up -d"
    Write-Host "Or:   .\scripts\init-wms-databases.ps1 -RestartServices"
}

Write-Host ""
Write-Host "Databases on localhost:5432:"
docker run --rm -e PGPASSWORD=postgres --add-host=host.docker.internal:host-gateway postgres:16-alpine `
    psql -h host.docker.internal -p 5432 -U postgres -tAc `
    "SELECT datname FROM pg_database WHERE datname LIKE 'wms_%' OR datname = 'keycloak_db' ORDER BY 1;" 2>$null |
    ForEach-Object { Write-Host "  $_" }

Write-Host ""
Write-Host "Table counts (all schemas):"
$dbs = @(
    @{ Name = "wms_core_db"; Note = "public" },
    @{ Name = "wms_localization_db"; Note = "public" },
    @{ Name = "wms_finance_db"; Note = "finance schema" },
    @{ Name = "wms_billing_db"; Note = "public" },
    @{ Name = "wms_integration_db"; Note = "public" },
    @{ Name = "wms_inbound_db"; Note = "public" },
    @{ Name = "wms_inventory_db"; Note = "public" },
    @{ Name = "wms_outbound_db"; Note = "public" }
)
foreach ($db in $dbs) {
    $count = docker run --rm -e PGPASSWORD=postgres --add-host=host.docker.internal:host-gateway postgres:16-alpine `
        psql -h host.docker.internal -p 5432 -U postgres -d $db.Name -tAc `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type='BASE TABLE';" 2>$null
    if ($LASTEXITCODE -ne 0) { $count = "n/a" }
    Write-Host ("  {0,-22} {1} tables ({2})" -f $db.Name, $count, $db.Note)
}
