<#
  Sunum hazirligi - WMS'e 15 dili ekler ve otomatik cevirileri tamamlatir.

  Akis:
    1. core-service (:8081) uzerinden JWT alir (demo.user / demo).
    2. Her dili localization-service (:8082) POST /api/v1/languages ile ekler.
    3. force=true ile auto-translate'i tetikler (modeller hazir -> tam MT).
    4. Her dil icin translation-status'u ready olana kadar yoklar.

  Kullanim:  powershell -ExecutionPolicy Bypass -File scripts\prepare-languages.ps1
#>

$ErrorActionPreference = "Stop"
$CoreUrl = "http://localhost:8081"
$LocUrl  = "http://localhost:8082"
$User    = "demo.user"
$Pass    = "demo"

# tr + en zaten seed. Eklenecek 13 dil (toplam 15).
$Languages = @(
    @{ code = "es"; name = "Espanol"    },
    @{ code = "fr"; name = "Francais"   },
    @{ code = "de"; name = "Deutsch"    },
    @{ code = "it"; name = "Italiano"   },
    @{ code = "pt"; name = "Portugues"  },
    @{ code = "ru"; name = "Russkiy"    },
    @{ code = "ar"; name = "Arabic"     },
    @{ code = "zh"; name = "Chinese"    },
    @{ code = "ja"; name = "Japanese"   },
    @{ code = "ko"; name = "Korean"     },
    @{ code = "hi"; name = "Hindi"      },
    @{ code = "nl"; name = "Nederlands" },
    @{ code = "pl"; name = "Polski"     }
)

function Get-Token {
    $body = @{ username = $User; password = $Pass } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$CoreUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body
    return $resp.accessToken
}

Write-Host "[prep] Token aliniyor..."
$token = Get-Token
$headers = @{ Authorization = "Bearer $token" }
Write-Host "[prep] Token alindi."

foreach ($lang in $Languages) {
    $code = $lang.code
    $body = @{ code = $code; name = $lang.name } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$LocUrl/api/v1/languages" -Method Post -Headers $headers -ContentType "application/json" -Body $body | Out-Null
        Write-Host "[prep] Dil eklendi: $code"
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match "409" -or $msg -match "exist" -or $msg -match "Conflict") {
            Write-Host "[prep] Dil zaten var: $code"
        } else {
            Write-Host "[prep] Ekleme hatasi ($code): $msg"
        }
    }
}

Write-Host ""
Write-Host "[prep] force=true auto-translate tetikleniyor (kaynak: tr)..."
foreach ($lang in $Languages) {
    $code = $lang.code
    try {
        $r = Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$code/auto-translate?force=true&source=tr" -Method Post -Headers $headers
        Write-Host "[prep] auto-translate: $code (async=$($r.async))"
    } catch {
        Write-Host "[prep] auto-translate hatasi ($code): $($_.Exception.Message)"
    }
}

Write-Host ""
Write-Host "[prep] Ceviri ilerlemesi izleniyor (ready hedef)..."
$deadline = (Get-Date).AddMinutes(45)
$pending = @($Languages | ForEach-Object { $_.code })
while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 15
    $stillPending = @()
    foreach ($code in $pending) {
        try {
            $st = Invoke-RestMethod -Uri "$LocUrl/api/v1/languages/$code/translation-status" -Method Get
            $ts = Get-Date -Format "HH:mm:ss"
            Write-Host ("[{0}] {1} -> {2}/{3} eksik={4} ready={5}" -f $ts, $code, $st.uiKeyCount, $st.expectedKeys, $st.missingKeys, $st.ready)
            if (-not $st.ready) { $stillPending += $code }
        } catch {
            $stillPending += $code
        }
    }
    $pending = $stillPending
}

Write-Host ""
if ($pending.Count -eq 0) {
    Write-Host "[prep] TUM DILLER HAZIR - ceviriler tamamlandi."
} else {
    Write-Host ("[prep] Suresi doldu. Bekleyen diller: {0}" -f ($pending -join ', '))
}
