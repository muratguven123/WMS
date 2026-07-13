<#
  Bir dili pasifleştirir (dil menüsünden kaldırır, çeviri verilerini temizler).

  Kullanim:
    powershell -ExecutionPolicy Bypass -File scripts\deactivate-language.ps1
    powershell -ExecutionPolicy Bypass -File scripts\deactivate-language.ps1 -Code rs
#>
param(
    [string]$Code = "rs"
)

$ErrorActionPreference = "Stop"
$CoreUrl = "http://localhost:8081"
$LocUrl  = "http://localhost:8082"

$body = @{ username = "demo.user"; password = "demo" } | ConvertTo-Json
$token = (Invoke-RestMethod -Uri "$CoreUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body).accessToken
$headers = @{ Authorization = "Bearer $token" }

$normalized = $Code.Trim().ToLower()
Write-Host "[deactivate] Pasifleştiriliyor: $normalized"

try {
    $result = Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$normalized" -Method Delete -Headers $headers
    Write-Host ("[deactivate] Tamam -> code={0} isActive={1}" -f $result.code, $result.isActive)
} catch {
    Write-Host ("[deactivate] Hata: {0}" -f $_.Exception.Message)
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    exit 1
}

$active = Invoke-RestMethod -Uri "$LocUrl/api/v1/languages" -Method Get -Headers $headers
Write-Host ("[deactivate] Aktif diller ({0}): {1}" -f $active.Count, (($active | ForEach-Object { $_.code }) -join ', '))
