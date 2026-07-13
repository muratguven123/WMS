$root = "c:\Users\murat\Desktop\WMS"
$replacements = @{
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000001")' = '1L'
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000002")' = '2L'
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000010")' = '10L'
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000011")' = '11L'
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000012")' = '12L'
    'Long.parseLong("aaaaaaaa-0000-0000-0000-000000000020")' = '20L'
    'Long.parseLong("bbbbbbbb-0000-0000-0000-000000000001")' = '101L'
    'Long.parseLong("bbbbbbbb-0000-0000-0000-000000000002")' = '102L'
    'Long.parseLong("cccccccc-0000-0000-0000-000000000001")' = '201L'
    'Long.parseLong("dddddddd-0000-0000-0000-000000000001")' = '301L'
    'Long.parseLong("b1000000-0000-0000-0000-000000000001")' = '1001L'
    'Long.parseLong("b1000000-0000-0000-0000-000000000002")' = '1002L'
    'Long.parseLong("f3000000-0000-0000-0000-000000000001")' = '3001L'
    'Long.parseLong("f4000000-0000-0000-0000-000000000001")' = '4001L'
}

Get-ChildItem -Path $root -Recurse -Filter "*Test.java" | Where-Object { $_.FullName -notmatch '\\target\\' } | ForEach-Object {
    $content = [IO.File]::ReadAllText($_.FullName)
    $original = $content
    foreach ($k in $replacements.Keys) {
        $content = $content.Replace($k, $replacements[$k])
    }
    if ($content -ne $original) {
        [IO.File]::WriteAllText($_.FullName, $content)
        Write-Host "Fixed: $($_.Name)"
    }
}
