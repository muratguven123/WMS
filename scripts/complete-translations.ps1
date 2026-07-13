<#
  Kalan dillerin UI cevirisini SIRAYLA tamamlar.
  Backend tek is parcacigi ile ceviri yaptigindan, ayni anda ikinci
  auto-translate istegi 500 doner. Bu script her dili tek tek tetikler
  ve o dil 'ready' olana kadar bekler, sonra digerine gecer.

  Kullanim: powershell -ExecutionPolicy Bypass -File scripts\complete-translations.ps1
#>

$ErrorActionPreference = "Stop"
$CoreUrl = "http://localhost:8081"
$LocUrl  = "http://localhost:8082"

$b = @{ username = "demo.user"; password = "demo" } | ConvertTo-Json
$tok = (Invoke-RestMethod -Uri "$CoreUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $b).accessToken
$H = @{ Authorization = "Bearer $tok" }
Write-Host "[done] Token alindi."

function Get-Status($code) {
    return Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$code/translation-status" -Method Get -Headers $H
}

# Sunumda gorunecek 15 dilin tamami (tr/en dahil kontrol edilir).
$AllCodes = @('en','tr','es','fr','de','it','pt','ru','ar','zh','ja','ko','hi','nl','pl')

$deadline = (Get-Date).AddMinutes(60)
foreach ($code in $AllCodes) {
    $st = Get-Status $code
    if ($st.ready) {
        Write-Host ("[done] {0} zaten hazir ({1}/{2})" -f $code, $st.uiKeyCount, $st.expectedKeys)
        continue
    }

    Write-Host ("[done] {0} cevriliyor (mevcut {1}/{2})..." -f $code, $st.uiKeyCount, $st.expectedKeys)
    # source=auto (en tam kapsama) -> tum 428 anahtar seed'lenir. 500 = zaten calisiyor, yok say.
    try {
        Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$code/auto-translate?force=true" -Method Post -Headers $H | Out-Null
    } catch {
        # 500 = zaten calisiyor; devam edip bekle
    }

    $lastCount = -1
    $stall = 0
    while (-not $st.ready -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 15
        try {
            $st = Get-Status $code
            $ts = Get-Date -Format "HH:mm:ss"
            Write-Host ("[{0}] {1} -> {2}/{3} eksik={4}" -f $ts, $code, $st.uiKeyCount, $st.expectedKeys, $st.missingKeys)
        } catch {
            # gecici hata; tekrar dene
        }
        if ($st.uiKeyCount -eq $lastCount) { $stall++ } else { $stall = 0 }
        $lastCount = $st.uiKeyCount
        # 3 turdur (45s) ilerleme yok ve hala hazir degil -> yeniden tetikle
        if (-not $st.ready -and $stall -ge 3) {
            try {
                Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$code/auto-translate?force=true" -Method Post -Headers $H | Out-Null
            } catch { }
            $stall = 0
        }
    }

    if ($st.ready) {
        Write-Host ("[done] {0} TAMAM ({1}/{2})" -f $code, $st.uiKeyCount, $st.expectedKeys)
    } else {
        Write-Host ("[done] {0} suresi doldu (eksik={1})" -f $code, $st.missingKeys)
    }
}

Write-Host ""
Write-Host "[done] Ozet:"
$notReady = @()
foreach ($code in $AllCodes) {
    $st = Get-Status $code
    Write-Host ("  {0}: {1}/{2} ready={3}" -f $code, $st.uiKeyCount, $st.expectedKeys, $st.ready)
    if (-not $st.ready) { $notReady += $code }
}
if ($notReady.Count -eq 0) {
    Write-Host "[done] TUM DILLER HAZIR."
} else {
    Write-Host ("[done] Bekleyen: {0}" -f ($notReady -join ', '))
}
