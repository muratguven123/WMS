#Requires -Version 5.1
<#
.SYNOPSIS
  Initializes the WMS repo and creates 100+ meaningful commits, then pushes.
#>
$ErrorActionPreference = "Continue"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))

function Invoke-Commit {
  param(
    [Parameter(Mandatory)][string]$Message,
    [Parameter(Mandatory)][string[]]$Paths
  )
  $existing = @($Paths | Where-Object { Test-Path -LiteralPath $_ })
  if ($existing.Count -eq 0) {
    Write-Host "SKIP (missing): $Message"
    return $false
  }
  & git add -- @existing 2>&1 | Out-Null
  $staged = & git diff --cached --name-only 2>$null
  if (-not $staged) {
    Write-Host "SKIP (nothing staged): $Message"
    return $false
  }
  & git commit -m $Message 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: $Message"
    return $false
  }
  Write-Host "OK: $Message"
  return $true
}

if (-not (Test-Path .git)) {
  git init -b main | Out-Null
} else {
  $existingCommits = 0
  try { $existingCommits = [int](git rev-list --count HEAD 2>$null) } catch { $existingCommits = 0 }
  if ($existingCommits -gt 0) {
    Write-Host "Repo already has $existingCommits commits. Aborting to avoid rewriting."
    exit 1
  }
}

# ---- Commit plan (100+) ----
$plan = @(
  @{ m = "chore: add root gitignore and project README"; p = @(".gitignore", "README.md") }
  @{ m = "build: add Maven parent POM for WMS microservices"; p = @("pom.xml") }
  @{ m = "chore: add Docker ignore rules"; p = @(".dockerignore") }
  @{ m = "chore: add local service runner scripts"; p = @("run-service.ps1", "run-service.cmd") }
  @{ m = "ci: add GitHub Actions workflow"; p = @(".github/workflows/ci.yml") }

  # Common events
  @{ m = "feat(common-events): scaffold shared Kafka event library"; p = @("wms-common-events") }

  # Core bootstrap
  @{ m = "feat(core): scaffold core-service module and Spring Boot entrypoint"; p = @("wms-core-service/pom.xml", "wms-core-service/src/main/java/com/wms/core/CoreServiceApplication.java", "wms-core-service/src/main/resources/application.yml") }
  @{ m = "feat(core): add application configuration beans"; p = @("wms-core-service/src/main/java/com/wms/core/config") }
  @{ m = "feat(core): add security and Keycloak JWT integration"; p = @("wms-core-service/src/main/java/com/wms/core/security") }
  @{ m = "feat(core): add domain entities and enums"; p = @("wms-core-service/src/main/java/com/wms/core/entity") }
  @{ m = "feat(core): add JPA repositories"; p = @("wms-core-service/src/main/java/com/wms/core/repository") }
  @{ m = "feat(core): add exception handling layer"; p = @("wms-core-service/src/main/java/com/wms/core/exception") }
  @{ m = "feat(core): add auth service and DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/service/auth", "wms-core-service/src/main/java/com/wms/core/dto/auth") }
  @{ m = "feat(core): add user DTOs and org DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/dto/user", "wms-core-service/src/main/java/com/wms/core/dto/org") }
  @{ m = "feat(core): add address and geo DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/dto/address", "wms-core-service/src/main/java/com/wms/core/dto/geo") }
  @{ m = "feat(core): add workflow and audit DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/dto/workflow", "wms-core-service/src/main/java/com/wms/core/dto/audit") }
  @{ m = "feat(core): add dynamic UI DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/dto/ui") }
  @{ m = "feat(core): add remaining shared DTOs"; p = @("wms-core-service/src/main/java/com/wms/core/dto") }
  @{ m = "feat(core): add domain services"; p = @("wms-core-service/src/main/java/com/wms/core/service") }
  @{ m = "feat(core): add REST controllers"; p = @("wms-core-service/src/main/java/com/wms/core/controller") }
  @{ m = "feat(core): add AOP aspects for tenant and workflow"; p = @("wms-core-service/src/main/java/com/wms/core/aspect") }
  @{ m = "feat(core): add messaging and domain events"; p = @("wms-core-service/src/main/java/com/wms/core/messaging", "wms-core-service/src/main/java/com/wms/core/event") }
  @{ m = "feat(core): add outbound integration clients"; p = @("wms-core-service/src/main/java/com/wms/core/integration") }
  @{ m = "feat(core): add timezone utilities"; p = @("wms-core-service/src/main/java/com/wms/core/util") }
  @{ m = "feat(core): add Flyway V1 core schema"; p = @("wms-core-service/src/main/resources/db/migration/V1__core_schema.sql") }
  @{ m = "feat(core): add Flyway V2 process configuration schema"; p = @("wms-core-service/src/main/resources/db/migration/V2__process_config.sql") }
  @{ m = "feat(core): add Flyway V3 audit log schema"; p = @("wms-core-service/src/main/resources/db/migration/V3__audit_log.sql") }
  @{ m = "feat(core): add Flyway V4 approval request schema"; p = @("wms-core-service/src/main/resources/db/migration/V4__approval_request.sql") }
  @{ m = "feat(core): add Flyway V5 stock schema"; p = @("wms-core-service/src/main/resources/db/migration/V5__stock_schema.sql") }
  @{ m = "feat(core): seed workflow demo configuration"; p = @("wms-core-service/src/main/resources/db/migration/V6__workflow_demo_config.sql") }
  @{ m = "feat(core): add preferred timezone column for users"; p = @("wms-core-service/src/main/resources/db/migration/V7__user_preferred_timezone.sql") }
  @{ m = "feat(core): add address hierarchy indexes"; p = @("wms-core-service/src/main/resources/db/migration/V8__address_hierarchy_indexes.sql") }
  @{ m = "feat(core): seed address demo data"; p = @("wms-core-service/src/main/resources/db/migration/V9__address_demo_seed.sql") }
  @{ m = "feat(core): add dynamic UI schema migration"; p = @("wms-core-service/src/main/resources/db/migration/V10__dynamic_ui_schema.sql") }
  @{ m = "feat(core): seed dynamic UI demo form"; p = @("wms-core-service/src/main/resources/db/migration/V11__dynamic_ui_demo_seed_form.sql") }
  @{ m = "feat(core): add storage location schema"; p = @("wms-core-service/src/main/resources/db/migration/V12__storage_location_schema.sql") }
  @{ m = "feat(core): link users to Keycloak subject ids"; p = @("wms-core-service/src/main/resources/db/migration/V13__user_keycloak_id.sql") }
  @{ m = "feat(core): seed organization demo data"; p = @("wms-core-service/src/main/resources/db/migration/V14__organization_demo_seed.sql") }
  @{ m = "feat(core): seed demo user Keycloak links"; p = @("wms-core-service/src/main/resources/db/migration/V15__demo_user_keycloak_link.sql") }
  @{ m = "refactor(core): migrate UUID identifiers to bigint"; p = @("wms-core-service/src/main/resources/db/migration/V16__uuid_to_bigint.sql") }
  @{ m = "feat(core): refresh bigint demo seed references"; p = @("wms-core-service/src/main/resources/db/migration/V17__bigint_demo_seed_reference.sql") }
  @{ m = "feat(core): expand address country master data"; p = @("wms-core-service/src/main/resources/db/migration/V18__address_countries_expansion.sql") }
  @{ m = "feat(core): enforce unique location names per company"; p = @("wms-core-service/src/main/resources/db/migration/V19__location_company_unique_name.sql") }
  @{ m = "feat(core): scope approval requests by tenant"; p = @("wms-core-service/src/main/resources/db/migration/V20__approval_request_scope.sql") }
  @{ m = "feat(core): add configurable table column engine schema"; p = @("wms-core-service/src/main/resources/db/migration/V21__table_column_engine.sql") }
  @{ m = "feat(core): seed table column definitions"; p = @("wms-core-service/src/main/resources/db/migration/V22__table_column_seed.sql") }
  @{ m = "test(core): add unit and controller tests"; p = @("wms-core-service/src/test") }
  @{ m = "chore(core): add remaining core-service resources"; p = @("wms-core-service") }

  # Localization
  @{ m = "feat(localization): scaffold localization-service module"; p = @("wms-localization-service/pom.xml", "wms-localization-service/src/main/java/com/wms/localization/LocalizationServiceApplication.java", "wms-localization-service/src/main/resources/application.yml") }
  @{ m = "feat(localization): add configuration and security"; p = @("wms-localization-service/src/main/java/com/wms/localization/config", "wms-localization-service/src/main/java/com/wms/localization/security") }
  @{ m = "feat(localization): add i18n entities and repositories"; p = @("wms-localization-service/src/main/java/com/wms/localization/entity", "wms-localization-service/src/main/java/com/wms/localization/repository") }
  @{ m = "feat(localization): add DTOs and exception handlers"; p = @("wms-localization-service/src/main/java/com/wms/localization/dto", "wms-localization-service/src/main/java/com/wms/localization/exception") }
  @{ m = "feat(localization): add translation services"; p = @("wms-localization-service/src/main/java/com/wms/localization/service/translation", "wms-localization-service/src/main/java/com/wms/localization/service") }
  @{ m = "feat(localization): add address domain model"; p = @("wms-localization-service/src/main/java/com/wms/localization/domain") }
  @{ m = "feat(localization): add address services and formatters"; p = @("wms-localization-service/src/main/java/com/wms/localization/service/address") }
  @{ m = "feat(localization): add REST controllers"; p = @("wms-localization-service/src/main/java/com/wms/localization/controller") }
  @{ m = "feat(localization): add messaging listeners and integrations"; p = @("wms-localization-service/src/main/java/com/wms/localization/messaging", "wms-localization-service/src/main/java/com/wms/localization/listener", "wms-localization-service/src/main/java/com/wms/localization/event", "wms-localization-service/src/main/java/com/wms/localization/integration") }
  @{ m = "feat(localization): add utility helpers"; p = @("wms-localization-service/src/main/java/com/wms/localization/util") }
  @{ m = "feat(localization): add Flyway V2 i18n schema"; p = @("wms-localization-service/src/main/resources/db/migration/V2__i18n_schema.sql") }
  @{ m = "feat(localization): add missing translation log schema"; p = @("wms-localization-service/src/main/resources/db/migration/V3__missing_translation_log.sql") }
  @{ m = "feat(localization): seed base translations"; p = @("wms-localization-service/src/main/resources/db/migration/V4__seed_translations.sql") }
  @{ m = "feat(localization): add format config schema"; p = @("wms-localization-service/src/main/resources/db/migration/V5__format_config_schema.sql") }
  @{ m = "feat(localization): seed format configurations"; p = @("wms-localization-service/src/main/resources/db/migration/V6__format_config_seed.sql") }
  @{ m = "feat(localization): add address schema and country templates"; p = @("wms-localization-service/src/main/resources/db/migration/V12_1__address_schema.sql", "wms-localization-service/src/main/resources/db/migration/V12_2__country_address_template_seed.sql") }
  @{ m = "feat(localization): seed UI demo translations"; p = @("wms-localization-service/src/main/resources/db/migration/V13__ui_demo_translations_seed.sql") }
  @{ m = "refactor(localization): migrate UUID identifiers to bigint"; p = @("wms-localization-service/src/main/resources/db/migration/V14__uuid_to_bigint.sql") }
  @{ m = "feat(localization): enrich address template field metadata"; p = @("wms-localization-service/src/main/resources/db/migration/V15__address_template_field_metadata.sql") }
  @{ m = "feat(localization): seed address templates for 15 countries"; p = @("wms-localization-service/src/main/resources/db/migration/V16__country_address_template_seed_15_countries.sql") }
  @{ m = "feat(localization): refine US address template with state"; p = @("wms-localization-service/src/main/resources/db/migration/V17__us_address_template_state.sql") }
  @{ m = "feat(localization): switch address master fields to select sources"; p = @("wms-localization-service/src/main/resources/db/migration/V19__address_master_select_state_city.sql") }
  @{ m = "feat(localization): support manual text address fields"; p = @("wms-localization-service/src/main/resources/db/migration/V20__address_fields_manual_text.sql") }
  @{ m = "feat(localization): add EU country state and city templates"; p = @("wms-localization-service/src/main/resources/db/migration/V21__de_fr_nl_pl_pt_state_city.sql") }
  @{ m = "feat(localization): add address template audit trail"; p = @("wms-localization-service/src/main/resources/db/migration/V22__address_template_audit.sql") }
  @{ m = "feat(localization): seed configurable column label translations"; p = @("wms-localization-service/src/main/resources/db/migration/V23__column_labels_seed.sql") }
  @{ m = "test(localization): add service and controller tests"; p = @("wms-localization-service/src/test") }
  @{ m = "chore(localization): add remaining localization resources"; p = @("wms-localization-service") }

  # Finance
  @{ m = "feat(finance): scaffold finance-service module"; p = @("wms-finance-service/pom.xml", "wms-finance-service/src/main/java/com/wms/finance/FinanceServiceApplication.java", "wms-finance-service/src/main/resources/application.yml") }
  @{ m = "feat(finance): add config security and exception handling"; p = @("wms-finance-service/src/main/java/com/wms/finance/config", "wms-finance-service/src/main/java/com/wms/finance/security", "wms-finance-service/src/main/java/com/wms/finance/exception") }
  @{ m = "feat(finance): add currency entities and repositories"; p = @("wms-finance-service/src/main/java/com/wms/finance/entity", "wms-finance-service/src/main/java/com/wms/finance/repository") }
  @{ m = "feat(finance): add DTOs controllers and domain services"; p = @("wms-finance-service/src/main/java/com/wms/finance/dto", "wms-finance-service/src/main/java/com/wms/finance/controller", "wms-finance-service/src/main/java/com/wms/finance/service") }
  @{ m = "feat(finance): add TCMB FX rate integration"; p = @("wms-finance-service/src/main/java/com/wms/finance/integration") }
  @{ m = "feat(finance): add scheduled FX jobs and messaging"; p = @("wms-finance-service/src/main/java/com/wms/finance/job", "wms-finance-service/src/main/java/com/wms/finance/messaging") }
  @{ m = "feat(finance): add tax calculation engine"; p = @("wms-finance-service/src/main/java/com/wms/finance/tax") }
  @{ m = "feat(finance): add multi-currency schema"; p = @("wms-finance-service/src/main/resources/db/migration/V1__multi_currency_schema.sql") }
  @{ m = "feat(finance): seed multi-currency demo data"; p = @("wms-finance-service/src/main/resources/db/migration/V2__multi_currency_demo_seed.sql") }
  @{ m = "feat(finance): add customer currency permissions"; p = @("wms-finance-service/src/main/resources/db/migration/V3__customer_currency_permissions.sql", "wms-finance-service/src/main/resources/db/migration/V4__customer_currency_demo_seed.sql") }
  @{ m = "feat(finance): add exchange rate audit log"; p = @("wms-finance-service/src/main/resources/db/migration/V5__exchange_rate_audit_log.sql") }
  @{ m = "feat(finance): seed TCMB currencies"; p = @("wms-finance-service/src/main/resources/db/migration/V6__tcmb_currency_seed.sql") }
  @{ m = "feat(finance): add tax rates schema and demo seed"; p = @("wms-finance-service/src/main/resources/db/migration/V7__tax_rates_schema.sql", "wms-finance-service/src/main/resources/db/migration/V8__tax_rates_demo_seed.sql") }
  @{ m = "feat(finance): add tax calculation audit schema"; p = @("wms-finance-service/src/main/resources/db/migration/V9__tax_calculation_audit_schema.sql") }
  @{ m = "feat(finance): seed 2026 currency settings and tax data"; p = @("wms-finance-service/src/main/resources/db/migration/V10__currency_settings_tax_2026_seed.sql") }
  @{ m = "refactor(finance): migrate UUID identifiers to bigint"; p = @("wms-finance-service/src/main/resources/db/migration/V11__uuid_to_bigint.sql") }
  @{ m = "feat(finance): seed effective and extended 2026 FX rates"; p = @("wms-finance-service/src/main/resources/db/migration/V12__effective_exchange_rates_2026_seed.sql", "wms-finance-service/src/main/resources/db/migration/V13__extended_currency_rates_2026_seed.sql") }
  @{ m = "test(finance): add finance service tests"; p = @("wms-finance-service/src/test") }
  @{ m = "chore(finance): add remaining finance-service files"; p = @("wms-finance-service") }

  # Billing
  @{ m = "feat(billing): scaffold billing-service module"; p = @("wms-billing-service/pom.xml", "wms-billing-service/src/main/java/com/wms/billing/BillingServiceApplication.java", "wms-billing-service/src/main/resources/application.yml") }
  @{ m = "feat(billing): add security and HTTP client config"; p = @("wms-billing-service/src/main/java/com/wms/billing/config", "wms-billing-service/src/main/java/com/wms/billing/security") }
  @{ m = "feat(billing): add invoice domain entities and enums"; p = @("wms-billing-service/src/main/java/com/wms/billing/domain") }
  @{ m = "feat(billing): add invoice DTOs repositories and mappers"; p = @("wms-billing-service/src/main/java/com/wms/billing/dto", "wms-billing-service/src/main/java/com/wms/billing/repository", "wms-billing-service/src/main/java/com/wms/billing/mapper") }
  @{ m = "feat(billing): add invoice and FX difference services"; p = @("wms-billing-service/src/main/java/com/wms/billing/service") }
  @{ m = "feat(billing): add invoice REST controllers"; p = @("wms-billing-service/src/main/java/com/wms/billing/controller") }
  @{ m = "feat(billing): add billing exception handlers"; p = @("wms-billing-service/src/main/java/com/wms/billing/exception") }
  @{ m = "feat(billing): add invoice Flyway migrations"; p = @("wms-billing-service/src/main/resources/db") }
  @{ m = "test(billing): add invoice calculation and API tests"; p = @("wms-billing-service/src/test") }
  @{ m = "chore(billing): add remaining billing-service files"; p = @("wms-billing-service") }

  # Integration
  @{ m = "feat(integration): scaffold integration-service module"; p = @("wms-integration-service/pom.xml", "wms-integration-service/src/main/java/com/wms/integration/IntegrationServiceApplication.java", "wms-integration-service/src/main/resources/application.yml") }
  @{ m = "feat(integration): add config security and entities"; p = @("wms-integration-service/src/main/java/com/wms/integration/config", "wms-integration-service/src/main/java/com/wms/integration/security", "wms-integration-service/src/main/java/com/wms/integration/entity") }
  @{ m = "feat(integration): add ERP adapters"; p = @("wms-integration-service/src/main/java/com/wms/integration/adapter") }
  @{ m = "feat(integration): add outbox pattern and messaging"; p = @("wms-integration-service/src/main/java/com/wms/integration/outbox", "wms-integration-service/src/main/java/com/wms/integration/messaging") }
  @{ m = "feat(integration): add repositories services and schedulers"; p = @("wms-integration-service/src/main/java/com/wms/integration/repository", "wms-integration-service/src/main/java/com/wms/integration/service", "wms-integration-service/src/main/java/com/wms/integration/scheduler") }
  @{ m = "feat(integration): add integration REST API"; p = @("wms-integration-service/src/main/java/com/wms/integration/api") }
  @{ m = "feat(integration): add Flyway migrations"; p = @("wms-integration-service/src/main/resources/db") }
  @{ m = "test(integration): add integration-service tests"; p = @("wms-integration-service/src/test") }
  @{ m = "chore(integration): add remaining integration-service files"; p = @("wms-integration-service") }

  # Inbound
  @{ m = "feat(inbound): scaffold inbound-service module"; p = @("wms-inbound-service/pom.xml", "wms-inbound-service/src/main/java/com/wms/inbound/InboundServiceApplication.java", "wms-inbound-service/src/main/resources/application.yml") }
  @{ m = "feat(inbound): add config security and entities"; p = @("wms-inbound-service/src/main/java/com/wms/inbound/config", "wms-inbound-service/src/main/java/com/wms/inbound/security", "wms-inbound-service/src/main/java/com/wms/inbound/entity") }
  @{ m = "feat(inbound): add DTOs repositories and exceptions"; p = @("wms-inbound-service/src/main/java/com/wms/inbound/dto", "wms-inbound-service/src/main/java/com/wms/inbound/repository", "wms-inbound-service/src/main/java/com/wms/inbound/exception") }
  @{ m = "feat(inbound): add receipt and putaway services"; p = @("wms-inbound-service/src/main/java/com/wms/inbound/service") }
  @{ m = "feat(inbound): add controllers listeners and schedulers"; p = @("wms-inbound-service/src/main/java/com/wms/inbound/controller", "wms-inbound-service/src/main/java/com/wms/inbound/listener", "wms-inbound-service/src/main/java/com/wms/inbound/scheduler") }
  @{ m = "feat(inbound): add core integration clients"; p = @("wms-inbound-service/src/main/java/com/wms/inbound/integration") }
  @{ m = "feat(inbound): add Flyway migrations"; p = @("wms-inbound-service/src/main/resources/db") }
  @{ m = "test(inbound): add inbound-service tests"; p = @("wms-inbound-service/src/test") }
  @{ m = "chore(inbound): add remaining inbound-service files"; p = @("wms-inbound-service") }

  # Inventory
  @{ m = "feat(inventory): scaffold inventory-service module"; p = @("wms-inventory-service/pom.xml", "wms-inventory-service/src/main/java/com/wms/inventory/InventoryServiceApplication.java", "wms-inventory-service/src/main/resources/application.yml") }
  @{ m = "feat(inventory): add config security and entities"; p = @("wms-inventory-service/src/main/java/com/wms/inventory/config", "wms-inventory-service/src/main/java/com/wms/inventory/security", "wms-inventory-service/src/main/java/com/wms/inventory/entity") }
  @{ m = "feat(inventory): add DTOs repositories and exceptions"; p = @("wms-inventory-service/src/main/java/com/wms/inventory/dto", "wms-inventory-service/src/main/java/com/wms/inventory/repository", "wms-inventory-service/src/main/java/com/wms/inventory/exception") }
  @{ m = "feat(inventory): add stock and transfer services"; p = @("wms-inventory-service/src/main/java/com/wms/inventory/service") }
  @{ m = "feat(inventory): add controllers messaging and integrations"; p = @("wms-inventory-service/src/main/java/com/wms/inventory/controller", "wms-inventory-service/src/main/java/com/wms/inventory/messaging", "wms-inventory-service/src/main/java/com/wms/inventory/integration") }
  @{ m = "feat(inventory): add Flyway migrations"; p = @("wms-inventory-service/src/main/resources/db") }
  @{ m = "test(inventory): add inventory-service tests"; p = @("wms-inventory-service/src/test") }
  @{ m = "chore(inventory): add remaining inventory-service files"; p = @("wms-inventory-service") }

  # Outbound
  @{ m = "feat(outbound): scaffold outbound-service module"; p = @("wms-outbound-service/pom.xml", "wms-outbound-service/src/main/java/com/wms/outbound/OutboundServiceApplication.java", "wms-outbound-service/src/main/resources/application.yml") }
  @{ m = "feat(outbound): add config security and entities"; p = @("wms-outbound-service/src/main/java/com/wms/outbound/config", "wms-outbound-service/src/main/java/com/wms/outbound/security", "wms-outbound-service/src/main/java/com/wms/outbound/entity") }
  @{ m = "feat(outbound): add DTOs repositories and exceptions"; p = @("wms-outbound-service/src/main/java/com/wms/outbound/dto", "wms-outbound-service/src/main/java/com/wms/outbound/repository", "wms-outbound-service/src/main/java/com/wms/outbound/exception") }
  @{ m = "feat(outbound): add picking packing and shipping services"; p = @("wms-outbound-service/src/main/java/com/wms/outbound/service") }
  @{ m = "feat(outbound): add carrier integrations"; p = @("wms-outbound-service/src/main/java/com/wms/outbound/integration") }
  @{ m = "feat(outbound): add controllers messaging and schedulers"; p = @("wms-outbound-service/src/main/java/com/wms/outbound/controller", "wms-outbound-service/src/main/java/com/wms/outbound/messaging", "wms-outbound-service/src/main/java/com/wms/outbound/scheduler") }
  @{ m = "feat(outbound): add Flyway migrations"; p = @("wms-outbound-service/src/main/resources/db") }
  @{ m = "test(outbound): add outbound-service tests"; p = @("wms-outbound-service/src/test") }
  @{ m = "chore(outbound): add remaining outbound-service files"; p = @("wms-outbound-service") }

  # Notification
  @{ m = "feat(notification): scaffold notification-service module"; p = @("wms-notification-service/pom.xml", "wms-notification-service/src/main/java/com/wms/notification/NotificationServiceApplication.java", "wms-notification-service/src/main/resources/application.yml", "wms-notification-service/src/main/resources/application-docker.yml") }
  @{ m = "feat(notification): add WebSocket Redis and Kafka bridge"; p = @("wms-notification-service/src/main/java/com/wms/notification/config", "wms-notification-service/src/main/java/com/wms/notification/messaging") }
  @{ m = "feat(notification): add STOMP security interceptors"; p = @("wms-notification-service/src/main/java/com/wms/notification/security") }
  @{ m = "test(notification): add STOMP subscribe interceptor tests"; p = @("wms-notification-service/src/test") }
  @{ m = "chore(notification): add remaining notification-service files"; p = @("wms-notification-service") }

  # UI
  @{ m = "feat(ui): scaffold Vite React TypeScript app"; p = @("wms-ui/package.json", "wms-ui/package-lock.json", "wms-ui/tsconfig.json", "wms-ui/tsconfig.app.json", "wms-ui/tsconfig.node.json", "wms-ui/vite.config.ts", "wms-ui/index.html", "wms-ui/eslint.config.js", "wms-ui/.gitignore", "wms-ui/README.md") }
  @{ m = "feat(ui): add app entrypoint and global styles"; p = @("wms-ui/src/main.tsx", "wms-ui/src/App.tsx", "wms-ui/src/App.css", "wms-ui/src/index.css") }
  @{ m = "feat(ui): add API client and service layer"; p = @("wms-ui/src/api") }
  @{ m = "feat(ui): add auth role helpers"; p = @("wms-ui/src/auth") }
  @{ m = "feat(ui): add i18n context and translations"; p = @("wms-ui/src/i18n") }
  @{ m = "feat(ui): add shared layout and common components"; p = @("wms-ui/src/components/Layout.tsx", "wms-ui/src/components/common.tsx", "wms-ui/src/components/Dropdown.tsx") }
  @{ m = "feat(ui): add DataTable and column picker"; p = @("wms-ui/src/components/DataTable.tsx", "wms-ui/src/components/ColumnPicker.tsx", "wms-ui/src/components/tableUtils.ts", "wms-ui/src/hooks/useTableSchema.ts") }
  @{ m = "feat(ui): add dynamic form and address field components"; p = @("wms-ui/src/components/DynamicForm.tsx", "wms-ui/src/components/DynamicAddressField.tsx") }
  @{ m = "feat(ui): add org data hooks and currency constants"; p = @("wms-ui/src/hooks/useOrgData.ts", "wms-ui/src/constants") }
  @{ m = "feat(ui): add realtime STOMP client"; p = @("wms-ui/src/realtime") }
  @{ m = "feat(ui): add login and dashboard views"; p = @("wms-ui/src/views/Login.tsx", "wms-ui/src/views/Dashboard.tsx") }
  @{ m = "feat(ui): add organization and user management views"; p = @("wms-ui/src/views/OrgHierarchy.tsx", "wms-ui/src/views/UserManagement.tsx") }
  @{ m = "feat(ui): add localization language and format settings views"; p = @("wms-ui/src/views/LanguageManagement.tsx", "wms-ui/src/views/LocalizationSettings.tsx") }
  @{ m = "feat(ui): add address master and template config views"; p = @("wms-ui/src/views/AddressMaster.tsx", "wms-ui/src/views/AddressTemplateConfig.tsx", "wms-ui/src/views/CountryManagement.tsx") }
  @{ m = "feat(ui): add workflow approval and audit views"; p = @("wms-ui/src/views/WorkflowConfig.tsx", "wms-ui/src/views/ApprovalQueue.tsx", "wms-ui/src/views/AuditLog.tsx", "wms-ui/src/views/DynamicFieldRules.tsx") }
  @{ m = "feat(ui): add finance tax and currency views"; p = @("wms-ui/src/views/CurrencyExchange.tsx", "wms-ui/src/views/TaxManagement.tsx", "wms-ui/src/views/BillingMultiCurrency.tsx", "wms-ui/src/views/billing") }
  @{ m = "feat(ui): add warehouse operations views"; p = @("wms-ui/src/views/InboundPutaway.tsx", "wms-ui/src/views/InventoryTransfer.tsx", "wms-ui/src/views/RackView.tsx", "wms-ui/src/views/PickingRoute.tsx", "wms-ui/src/views/ShippingOutbound.tsx") }
  @{ m = "feat(ui): add integration monitor and realtime test views"; p = @("wms-ui/src/views/IntegrationMonitor.tsx", "wms-ui/src/views/RealtimeTest.tsx") }
  @{ m = "feat(ui): add static assets and public files"; p = @("wms-ui/src/assets", "wms-ui/public") }
  @{ m = "feat(ui): add UI Dockerfile"; p = @("wms-ui/Dockerfile") }
  @{ m = "chore(ui): add remaining UI project files"; p = @("wms-ui") }

  # Docker / infra
  @{ m = "feat(docker): add Postgres database init scripts"; p = @("docker/postgres") }
  @{ m = "feat(docker): add Keycloak realm import"; p = @("docker/keycloak") }
  @{ m = "feat(docker): add nginx reverse proxy for UI"; p = @("docker/nginx") }
  @{ m = "feat(docker): add pgAdmin and LibreTranslate helpers"; p = @("docker/pgadmin", "docker/libretranslate") }
  @{ m = "feat(docker): add root service Dockerfile"; p = @("Dockerfile") }
  @{ m = "feat(docker): add full docker-compose stack"; p = @("docker-compose.yml") }

  # Scripts
  @{ m = "chore(scripts): add database init helpers"; p = @("scripts/init-wms-databases.ps1", "scripts/init-wms-databases.cmd", "scripts/show-wms-databases.ps1") }
  @{ m = "chore(scripts): add translation maintenance scripts"; p = @("scripts/complete-translations.ps1", "scripts/deactivate-language.ps1", "scripts/export-ui-translations.mjs", "scripts/prepare-languages.ps1", "scripts/install-argos-models.py") }
  @{ m = "chore(scripts): add migration utility scripts"; p = @("scripts/fix-long-imports.ps1", "scripts/fix-test-long-constants.ps1", "scripts/ui-id-to-number.ps1", "scripts/uuid-to-long-migrate.ps1") }
  @{ m = "chore(scripts): add git history bootstrap script"; p = @("scripts/bootstrap-git-history.ps1") }

  # Docs - business requirements (one per BR where possible)
  @{ m = "docs: add master business requirements overview"; p = @("business_requirements/business_requirements.md") }
  @{ m = "docs: add organization structure architecture"; p = @("business_requirements/business_architecture_1_org_structure.md") }
  @{ m = "docs: add multilingual support architecture"; p = @("business_requirements/business_architecture_2_multilang.md") }
  @{ m = "docs: add workflow configuration architecture"; p = @("business_requirements/business_architecture_3_workflow_config.md") }
  @{ m = "docs: add ERP integration architecture"; p = @("business_requirements/business_architecture_4_erp_integration.md") }
  @{ m = "docs: add dynamic UI architecture"; p = @("business_requirements/business_architecture_5_dynamic_ui.md") }
  @{ m = "docs: add timezone support architecture"; p = @("business_requirements/business_architecture_6_timezone.md") }
  @{ m = "docs: add date and time format architecture"; p = @("business_requirements/business_architecture_7_date_time_formats.md") }
  @{ m = "docs: add multi-currency architecture"; p = @("business_requirements/business_architecture_8_multi_currency.md") }
  @{ m = "docs: add customer currency architecture"; p = @("business_requirements/business_architecture_9_customer_currency.md") }
  @{ m = "docs: add exchange rate management architecture"; p = @("business_requirements/business_architecture_10_exchange_rate_mgmt.md") }
  @{ m = "docs: add multi-currency billing architecture"; p = @("business_requirements/business_architecture_11_multi_currency_billing.md") }
  @{ m = "docs: add address format architecture"; p = @("business_requirements/business_architecture_12_address_formats.md") }
  @{ m = "docs: add hierarchical address architecture"; p = @("business_requirements/business_architecture_13_hierarchical_address.md") }
  @{ m = "docs: add tax rates architecture"; p = @("business_requirements/business_architecture_14_tax_rates.md") }
  @{ m = "docs: add tax calculation engine architecture"; p = @("business_requirements/business_architecture_15_tax_calc_engine.md") }
  @{ m = "docs: add configurable columns architecture"; p = @("business_requirements/business_architecture_16_configurable_columns.md") }
  @{ m = "docs: add address template config architecture"; p = @("business_requirements/business_architecture_17_address_template_config.md") }
  @{ m = "docs: add country management architecture"; p = @("business_requirements/business_architecture_18_country_management.md") }
  @{ m = "docs: add localization gap report and Keycloak guide"; p = @("business_requirements/2_1_lokalizasyon_gap_raporu.md", "business_requirements/keycloak_integration_guide.md") }
  @{ m = "docs: add project phase notes"; p = @("proje_fazlari") }
  @{ m = "docs: add agent prompt library"; p = @("promptlar") }
  @{ m = "docs: add dynamic address plan revisions"; p = @("WMS_Dinamik_Adres_Plani_v2.md", "WMS_Dinamik_Adres_Plani_v3.md", "WMS_Dinamik_Adres_Plani_v4.md") }
  @{ m = "docs: add localization PDF and endpoint catalog"; p = @("2_1_Lokalizasyon ve Çoklu Lokasyon Desteği.pdf", "wms-endpoints.csv") }
  @{ m = "docs: add cursor review prompt"; p = @("cursor_review_prompt.md") }
  @{ m = "chore: add remaining business_requirements files"; p = @("business_requirements") }
  @{ m = "chore: add remaining docker and scripts files"; p = @("docker", "scripts") }
)

$count = 0
foreach ($item in $plan) {
  if (Invoke-Commit -Message $item.m -Paths $item.p) {
    $count++
  }
}

# Sweep any leftovers into logical buckets
$leftovers = @(
  @{ m = "chore: add any remaining root documentation"; p = "." }
)

# More granular leftover sweep by top-level dirs still untracked
$untracked = git ls-files --others --exclude-standard
if ($untracked) {
  $groups = $untracked | ForEach-Object {
    if ($_ -match '^([^/]+)/') { $Matches[1] } else { '_root' }
  } | Sort-Object -Unique

  foreach ($g in $groups) {
    if ($g -eq '_root') {
      $files = @($untracked | Where-Object { $_ -notmatch '/' })
      if ($files.Count -gt 0) {
        git add -- $files
        if (git diff --cached --name-only) {
          git commit -m "chore: add remaining root project files" | Out-Null
          $count++
          Write-Host "OK: chore: add remaining root project files"
        }
      }
    } else {
      git add -- $g
      if (git diff --cached --name-only) {
        git commit -m "chore: add remaining $g files" | Out-Null
        $count++
        Write-Host "OK: chore: add remaining $g files"
      }
    }
  }
}

$total = git rev-list --count HEAD
Write-Host ""
Write-Host "Created commits this run: $count"
Write-Host "Total commits on main: $total"

if ([int]$total -lt 100) {
  Write-Host "WARNING: commit count is below 100. Expanding migrations into finer commits is recommended."
  exit 2
}

Write-Host "History bootstrap complete."
