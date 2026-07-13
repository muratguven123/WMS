param(
    [string]$Module = "wms-core-service"
)

Write-Host "Starting $Module ..."
mvn spring-boot:run -pl $Module @args
