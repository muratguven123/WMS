# Bulk update TypeScript ID types from string to number
$root = "c:\Users\murat\Desktop\WMS\wms-ui\src"

$tsFiles = Get-ChildItem -Path $root -Recurse -Include "*.ts","*.tsx"

foreach ($file in $tsFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    # ID field types
    $content = $content -replace '\bid: string\b', 'id: number'
    $content = $content -replace '\bcompanyId: string\b', 'companyId: number'
    $content = $content -replace '\blocationId: string\b', 'locationId: number'
    $content = $content -replace '\bcountryId: string\b', 'countryId: number'
    $content = $content -replace '\bcustomerId: string\b', 'customerId: number'
    $content = $content -replace '\bcurrencyId: string\b', 'currencyId: number'
    $content = $content -replace '\bcontractId: string\b', 'contractId: number'
    $content = $content -replace '\borderId: string\b', 'orderId: number'
    $content = $content -replace '\bstepConfigId: string\b', 'stepConfigId: number'
    $content = $content -replace '\breferenceId: string\b', 'referenceId: number'
    $content = $content -replace '\brequestedByUserId: string\b', 'requestedByUserId: number'
    $content = $content -replace '\bapprovedByUserId\?: string\b', 'approvedByUserId?: number'
    $content = $content -replace '\bscreenFieldId: string\b', 'screenFieldId: number'
    $content = $content -replace '\broleId\?: string\b', 'roleId?: number'
    $content = $content -replace '\bresponsibleRoleId\?: string\b', 'responsibleRoleId?: number'
    $content = $content -replace '\blogId: string\b', 'logId: number'
    $content = $content -replace '\boutboxMessageId\?: string\b', 'outboxMessageId?: number'
    $content = $content -replace '\bstorageLocationId: string\b', 'storageLocationId: number'
    $content = $content -replace '\bwarehouseLocationId: string\b', 'warehouseLocationId: number'
    $content = $content -replace '\buserId: string\b', 'userId: number'

    # Nullable ID refs in UpsertRuleRequest
    $content = $content -replace '\bcompanyId\?: string\b', 'companyId?: number'
    $content = $content -replace '\bcountryId\?: string\b', 'countryId?: number'
    $content = $content -replace '\blocationId\?: string\b', 'locationId?: number'
    $content = $content -replace '\broleId\?: string \| null\b', 'roleId?: number | null'
    $content = $content -replace '\bcontractId\?: string \| null\b', 'contractId?: number | null'

    # Function params
    $content = $content -replace '\(companyId: string\)', '(companyId: number)'
    $content = $content -replace '\(locationId: string\)', '(locationId: number)'
    $content = $content -replace '\(id: string,', '(id: number,'
    $content = $content -replace 'setActiveCompanyId: \(id: string\)', 'setActiveCompanyId: (id: number)'
    $content = $content -replace 'setActiveLocationId: \(id: string\)', 'setActiveLocationId: (id: number)'

    # Demo UUID constants -> numeric (V17 canonical demo IDs)
    $content = $content -replace '"22222222-0000-0000-0000-000000000001"', '1'
    $content = $content -replace '"22222222-0000-0000-0000-000000000002"', '2'
    $content = $content -replace '"bbbbbbbb-0000-0000-0000-000000000001"', '1'
    $content = $content -replace '"bbbbbbbb-0000-0000-0000-000000000002"', '2'
    $content = $content -replace '"11111111-0000-0000-0000-000000000001"', '1'

    # Labels
    $content = $content -replace 'Company UUID', 'Company ID'
    $content = $content -replace 'Location UUID', 'Location ID'
    $content = $content -replace 'Müşteri UUID', 'Müşteri ID'
    $content = $content -replace 'Para Birimi UUID', 'Para Birimi ID'
    $content = $content -replace 'Sözleşme UUID', 'Sözleşme ID'
    $content = $content -replace 'Customer UUID', 'Customer ID'
    $content = $content -replace 'Currency UUID', 'Currency ID'
    $content = $content -replace 'Contract UUID', 'Contract ID'
    $content = $content -replace '\(UUID\)', '(ID)'

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file.FullName, $content)
        Write-Host "Updated: $($file.Name)"
    }
}

Write-Host "UI migration complete."
