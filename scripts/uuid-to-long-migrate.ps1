# Bulk UUID -> Long migration for WMS Java sources
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path "$root\wms-core-service")) {
    $root = "c:\Users\murat\Desktop\WMS"
}

$javaFiles = Get-ChildItem -Path $root -Recurse -Filter "*.java" |
    Where-Object { $_.FullName -notmatch '\\target\\' }

$changed = 0
foreach ($file in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    # Skip if no UUID usage
    if ($content -notmatch 'UUID') { continue }

    # Preserve keycloak - already String, no change needed

    # Hibernate UUID generator
    $content = $content -replace '(?m)^\s*@UuidGenerator\r?\n', ''
    $content = $content -replace '(?m)^import org\.hibernate\.annotations\.UuidGenerator;\r?\n', ''

    # JPA generation strategy
    $content = $content -replace 'GenerationType\.UUID', 'GenerationType.IDENTITY'

    # Repository ID type
    $content = $content -replace 'JpaRepository<([^,>]+),\s*UUID>', 'JpaRepository<$1, Long>'

    # Path/request params
    $content = $content -replace '@PathVariable\s+UUID\b', '@PathVariable Long'
    $content = $content -replace '@RequestParam\s+UUID\b', '@RequestParam Long'
    $content = $content -replace '@RequestParam\(required = false\)\s+UUID\b', '@RequestParam(required = false) Long'
    $content = $content -replace '@Param\("([^"]+)"\)\s+UUID\b', '@Param("$1") Long'

    # Parsing / generation
    $content = $content -replace 'UUID\.fromString\(', 'Long.parseLong('
    $content = $content -replace 'UUID\.randomUUID\(\)', '1L'

    # Type references (after specific patterns)
    $content = $content -replace '\bUUID\b', 'Long'

    # Fix broken messages
    $content = $content -replace 'Invalid Long format', 'Invalid numeric ID format'
    $content = $content -replace 'is not a valid Long', 'is not a valid numeric ID'
    $content = $content -replace 'parseLongHeader', 'parseLongHeader'
    $content = $content -replace 'parseUuidHeader', 'parseLongHeader'

    # Remove unused UUID import
    $content = $content -replace '(?m)^import java\.util\.UUID;\r?\n', ''

    # Clean double blank lines
    $content = $content -replace '(\r?\n){3,}', "`n`n"

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file.FullName, $content)
        $changed++
        Write-Host "Updated: $($file.FullName)"
    }
}

Write-Host "Done. $changed files updated."
