$root = "c:\Users\murat\Desktop\WMS"
$javaFiles = Get-ChildItem -Path $root -Recurse -Filter "*.java" | Where-Object { $_.FullName -notmatch '\\target\\' }

foreach ($file in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    $content = $content -replace '(?m)^import java\.util\.Long;\r?\n', ''

    # Add IDENTITY to standalone @Id @Column id fields missing GeneratedValue
    $content = $content -replace '(@Id\r?\n)(\s*)(@Column\(name = "id")', '$1$2@GeneratedValue(strategy = GenerationType.IDENTITY)`n$2$3'

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file.FullName, $content)
    }
}

Write-Host "Fix complete."
