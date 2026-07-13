/**
 * WMS UI — Çoklu Servis API Katmanı
 * ==================================
 * Her mikroservise ayrı axios instance; hepsi aynı Keycloak JWT +
 * multi-tenant header interceptor'larını paylaşır.
 *
 *   core         :8081  — org, adres master, dynamic UI, workflow
 *   localization :8082  — dil, çeviri, format, adres kaydı
 *   finance      :8083  — kur, vergi, sipariş, sözleşme, işlem
 *   billing      :8084  — çoklu para birimi fatura
 *   integration  :8085  — ERP entegrasyon log & retry
 *   inbound      :8086  — mal kabul, QC, putaway
 *   inventory    :8087  — stok hareketi, tahsis, issue
 *   outbound     :8088  — toplama, paketleme, sevkiyat
 */

import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from "axios";
import { clearStoredTokens, ensureValidAccessToken, refreshAccessToken, tenantContext } from "./wms-api-client";

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const isOffline = () => Boolean((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode);

function withInterceptors(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
    if (!isOffline()) {
      const token = await ensureValidAccessToken(30);
      if (!token) {
        window.dispatchEvent(new CustomEvent("wms:auth-required"));
        return Promise.reject(new axios.Cancel("Oturum gerekli"));
      }
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    const companyId = tenantContext.getCompanyId();
    const locationId = tenantContext.getLocationId();
    if (companyId != null) config.headers.set("X-Active-Company-ID", String(companyId));
    if (locationId != null) config.headers.set("X-Active-Location-ID", String(locationId));
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const original = error.config as RetriableConfig | undefined;
      const status = error.response?.status;

      if (status === 401 && !isOffline() && original && !original._retry) {
        original._retry = true;
        try {
          const refreshed = await refreshAccessToken();
          if (refreshed?.accessToken) {
            original.headers.set("Authorization", `Bearer ${refreshed.accessToken}`);
            return instance(original);
          }
        } catch (err) {
          const code = (err as { response?: { data?: { errorCode?: string } } })?.response?.data?.errorCode;
          if (code === "AUTH_RELOGIN_REQUIRED") {
            clearStoredTokens();
          }
        }
        window.dispatchEvent(new CustomEvent("wms:auth-required"));
      }

      if (status === 403) {
        window.dispatchEvent(
          new CustomEvent("wms:tenant-forbidden", {
            detail: {
              companyId: tenantContext.getCompanyId(),
              locationId: tenantContext.getLocationId(),
              url: original?.url,
            },
          }),
        );
      }
      return Promise.reject(error);
    },
  );
  return instance;
}

/** Boş env → same-origin (nginx/vite proxy); dolu env → doğrudan servis URL'si. */
function resolveServiceBase(envValue: string | undefined): string {
  if (envValue != null && String(envValue).trim() !== "") {
    return String(envValue).replace(/\/$/, "");
  }
  return "";
}

const make = (envValue: string | undefined) =>
  withInterceptors(
    axios.create({
      baseURL: resolveServiceBase(envValue),
      timeout: 30_000,
    }),
  );

export const coreApi = make(import.meta.env.VITE_API_BASE_URL);
export const localizationApi = make(import.meta.env.VITE_LOCALIZATION_API_URL);
export const financeApi = make(import.meta.env.VITE_FINANCE_API_URL);
export const billingApi = make(import.meta.env.VITE_BILLING_API_URL);
export const integrationApi = make(import.meta.env.VITE_INTEGRATION_API_URL);
export const inboundApi = make(import.meta.env.VITE_INBOUND_API_URL);
export const inventoryApi = make(import.meta.env.VITE_INVENTORY_API_URL);
export const outboundApi = make(import.meta.env.VITE_OUTBOUND_API_URL);

/** Axios hatasını kullanıcıya gösterilebilir tek satıra indirger. */
export function describeError(e: unknown): string {
  const err = e as AxiosError<Record<string, unknown>>;
  if (err?.response) {
    const body = err.response.data;
    const msg =
      (typeof body === "object" && body && (body.message ?? body.error ?? body.detail)) ||
      err.response.statusText;
    return `HTTP ${err.response.status} — ${String(msg)}`;
  }
  if (err?.request) {
    const code = (err as AxiosError & { code?: string }).code;
    if (err.message === "Network Error" || code === "ERR_NETWORK") {
      return "Ağ/CORS hatası — tarayıcı backend yanıtına erişemedi. CORS ayarlarını veya dev proxy'yi kontrol edin.";
    }
    return "Servise ulaşılamadı (bağlantı hatası). Backend ayakta mı?";
  }
  return String((e as Error)?.message ?? e);
}

// ---------------------------------------------------------------------------
// Tipler
// ---------------------------------------------------------------------------

// Localization
export interface Language {
  id: number;
  code: string;
  name: string;
  isDefault?: boolean;
  isActive?: boolean;
  updatedAt?: string;
}
export interface ActiveFormatResponse {
  dateFormat: string;
  timeFormat: string;
  decimalSeparator: string;
  thousandSeparator: string;
}
export interface AddressPayload {
  countryId: number;
  city?: string;
  state?: string;
  zipCode?: string;
  addressDetails?: Record<string, unknown>;
}
export interface AddressResponse extends AddressPayload {
  id: number;
  formattedAddress?: string;
}
/**
 * Ülkeye özgü dinamik adres alanı — GET /api/addresses/templates/{countryId}.
 * fieldType/masterDataSource/parentFieldKey, UI'ın bu alanı nasıl (metin mi,
 * hangi cascade'e bağlı bir seçim mi) render edeceğini belirler; ülke adı
 * üzerinden hiçbir dallanma yapılmaz.
 */
export interface CountryAddressTemplateDto {
  id?: number;
  fieldId?: number;
  fieldKey: string;
  fieldLabelKey: string;
  mandatory: boolean;
  sequence: number;
  validationRegex?: string | null;
  errorMessageKey?: string | null;
  fieldType: "TEXT" | "MASTER_SELECT" | "FIXED";
  masterDataSource: "NONE" | "STATE" | "CITY" | "DISTRICT" | "NEIGHBORHOOD";
  parentFieldKey?: string | null;
}
export interface ImportResultDto {
  imported?: number;
  updated?: number;
  skipped?: number;
  errors?: string[];
  [k: string]: unknown;
}

// Core — adres master
export interface CountryDto { id: number; isoCode: string; name: string; }
export interface StateProvinceDto { id: number; name: string; code?: string; }
export interface NamedDto { id: number; name: string; }
export interface NeighborhoodDto { id: number; name: string; zipCode?: string; }

// Core — dynamic UI
export type FieldBehavior = "MANDATORY" | "OPTIONAL" | "HIDDEN" | "READ_ONLY";
export interface ResolvedFieldDto {
  fieldKey: string;
  labelKey: string;
  dataType: string;
  behavior: FieldBehavior;
  defaultValue?: string | null;
  validationRegex?: string | null;
  validationErrorMessageKey?: string | null;
}
export interface ResolvedScreenDto {
  screenCode: string;
  screenName: string;
  screenNameKey?: string;
  fields: ResolvedFieldDto[];
  resolvedAt?: string;
}

export type ColumnDataType = "STRING" | "NUMBER" | "DATE" | "BOOLEAN" | "CUSTOM";

export interface ResolvedColumnDto {
  columnDefId: number;
  key: string;
  labelKey: string;
  dataType: ColumnDataType;
  visible: boolean;
  sequence: number;
  locked: boolean;
  renderHint?: string | null;
  forceHidden: boolean;
  forceVisible: boolean;
}

export interface ResolvedTableSchemaDto {
  screenCode: string;
  columns: ResolvedColumnDto[];
  resolvedAt?: string;
}

export interface ColumnPreference {
  key: string;
  visible: boolean;
  sequence: number;
  width?: number | null;
}

export interface ColumnDefResponse {
  id: number;
  screenCode: string;
  columnKey: string;
  labelKey: string;
  dataType: ColumnDataType;
  defaultVisible: boolean;
  defaultSequence: number;
  locked: boolean;
  renderHint?: string | null;
}

export interface ColumnRuleResponse {
  id: number;
  tableColumnDefId: number;
  columnKey: string;
  screenCode: string;
  behavior: "FORCE_HIDDEN" | "FORCE_VISIBLE";
  priority: number;
  roleId?: number | null;
  companyId?: number | null;
}

export interface UpsertColumnDefRequest {
  columnKey: string;
  labelKey: string;
  dataType: ColumnDataType;
  defaultVisible: boolean;
  defaultSequence: number;
  locked: boolean;
  renderHint?: string | null;
}

export interface UpsertColumnRuleRequest {
  tableColumnDefId: number;
  priority?: number | null;
  roleId?: number | null;
  companyId?: number | null;
  behavior: "FORCE_HIDDEN" | "FORCE_VISIBLE";
}

export interface UpsertRuleRequest {
  screenFieldId: number;
  behavior: string;
  priority?: number | null;
  companyId?: number | null;
  countryId?: number | null;
  locationId?: number | null;
  roleId?: number | null;
  operationType?: string | null;
  defaultValue?: string | null;
  validationRegex?: string | null;
  validationErrorMessageKey?: string | null;
}
export interface RuleResponse extends UpsertRuleRequest { id: number; [k: string]: unknown; }

// Core — süreç konfigürasyonu
export interface StepConfig {
  id: number;
  stepCode?: string;
  stepName?: string;
  sequence?: number;
  mandatory?: boolean;
  active?: boolean;
  requiresApproval?: boolean;
  responsibleRoleId?: number | null;
  [k: string]: unknown;
}
export interface UpdateStepConfigRequest {
  sequence?: number;
  mandatory?: boolean;
  active?: boolean;
  requiresApproval?: boolean;
  responsibleRoleId?: number | null;
}

// Integration
export interface IntegrationLogResponse {
  id: number;
  locationId?: number;
  locationName?: string;
  erpSystemCode?: string;
  jobCode?: string;
  jobName?: string;
  status: string;
  externalReference?: string;
  errorMessage?: string;
  createdAt?: string;
  lastAttemptAt?: string;
  outboxMessageId?: number;
  requestPayload?: string;
  responsePayload?: string;
}
export interface PageResp<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface RetryResponse {
  logId: number;
  outboxMessageId?: number;
  message?: string;
  scheduledAt?: string;
}

// Finance
export type RateType = "BUYING" | "SELLING" | "EFFECTIVE_BUYING" | "EFFECTIVE_SELLING";
export interface ExchangeRateDto {
  sourceCurrency: string;
  targetCurrency: string;
  rate: number;
  rateDate: string;
  rateType: string;
  rateSource: string;
  fallbackUsed: boolean;
}
export interface ActiveRateListDto {
  baseCurrency: string;
  rateDate: string;
  rateType: string;
  rates: ExchangeRateDto[];
  lastTcmbSyncAt?: string | null;
}
export interface TcmbSyncResponse {
  success: boolean;
  inserted: number;
  updated: number;
  skipped: number;
  rateDate?: string | null;
}
export interface ManualRateRequest {
  sourceCurrency: string;
  targetCurrency: string;
  rateDate: string;
  rateType: RateType;
  rate: number;
}
export interface ManualRateResponse extends ManualRateRequest {
  id: number;
  action?: string;
  previousRate?: number | null;
}
export interface TaxRateResponse {
  id: number;
  taxTypeCode: string;
  countryId?: number;
  locationId?: number;
  customerId?: string;
  productType?: string;
  operationType?: string;
  rate: number;
  startDate?: string;
  endDate?: string;
  active: boolean;
  createdAt?: string;
}
export interface TaxRateVersionResult {
  expiredRate?: TaxRateResponse;
  newRate?: TaxRateResponse;
}
export interface OrderRequest { customerId: number; currencyId: number; amount: number; contractId?: number | null; }
export interface OrderResponse {
  orderId: number;
  customerId: number;
  currencyId: number;
  currencyCode: string;
  amount: number;
  orderDate: string;
}
export interface ContractDto {
  id: number;
  customerId: number;
  currencyId: number;
  currencyCode: string;
  contractCode: string;
  startDate?: string;
  endDate?: string;
}
export interface FinancialTransactionDto {
  id: number;
  companyId?: number;
  locationId?: number;
  contractId?: string;
  originalCurrencyCode: string;
  originalAmount: number;
  exchangeRate: number;
  baseCurrencyCode: string;
  convertedAmount: number;
  fallbackRateUsed: boolean;
}

// Core — organizasyon
export interface CompanySummaryDto {
  id: number;
  name: string;
  taxNumber?: string;
}
export interface LocationSummaryDto {
  id: number;
  companyId: number;
  name: string;
  timezone: string;
  active: boolean;
}

export type LocationType = "CENTRAL" | "TRANSIT" | "VIRTUAL" | "DISTRIBUTION" | "RETURN";

export interface LocationDetailDto {
  id: number;
  companyId: number;
  regionId: number;
  name: string;
  type: LocationType;
  timezone: string;
  active: boolean;
}

export interface CreateLocationRequest {
  name: string;
  type: LocationType;
  timezone: string;
  regionId: number;
  templateLocationId?: number | null;
}

export interface UpdateLocationRequest {
  name?: string;
  type?: LocationType;
  timezone?: string;
  regionId?: number;
}

export interface RegionSummaryDto {
  id: number;
  countryId: number;
  name: string;
}

// Core — onay kuyruğu
export interface ApprovalRequestDto {
  id: number;
  stepConfigId: number;
  referenceType: string;
  referenceId: number;
  requestedByUserId: number;
  approvedByUserId?: number | null;
  status: string;
  reviewerNote?: string | null;
  createdAt?: string;
  approvedAt?: string | null;
}

// Finance — vergi hesaplama
export interface TaxCalculateRequest {
  amount: number;
  taxTypeCode: string;
  mode: "INCLUSIVE" | "EXCLUSIVE";
  countryId: number;
  locationId?: number | null;
  customerId?: string | null;
  productType?: string | null;
  operationType?: string | null;
  transactionDate?: string;
}
export interface TaxCalculateResponse {
  net: number;
  tax: number;
  gross: number;
  taxTypeCode: string;
  rate: number;
  inclusive: boolean;
}

// Audit log
export interface AuditLogEntry {
  id: number;
  entityName: string;
  entityId: string;
  actionType: string;
  fieldName: string;
  oldValue?: string | null;
  newValue?: string | null;
  changedByUserId?: string | null;
  changedAt: string;
}

// Inbound
export interface ReceiptItemResponse {
  id: number;
  productCode: string;
  quantity?: number;
  expectedQuantity?: number;
  receivedQuantity?: number;
  lotNumber?: string;
  qcPassed?: boolean;
}
export interface ReceiptResponse {
  id: number;
  inboundOrderId: string;
  receiptNumber: string;
  receivedByUserId?: string;
  receivedAt?: string;
  status: string;
  items: ReceiptItemResponse[];
}
export interface PutawayRecommendation {
  productCode: string;
  recommendedBinId: string;
  recommendedBinCode?: string;
  reason?: string;
}

// Inventory
export interface StockItemDto {
  id: number;
  sku: string;
  quantity: number;
  binId?: string;
  lot?: string;
}
export interface InternalMoveRequest {
  productCode: string;
  lotNumber?: string;
  quantity: number;
  sourceBinId: string;
  targetBinId: string;
  movedByUserId: string;
}

// Outbound
export interface PickingItemDto {
  id: number;
  productCode: string;
  quantity?: number;
  quantityToPick?: number;
  pickedQuantity?: number;
  sourceLocationId?: string;
  addressCode?: string;
  status?: string;
  picked?: boolean;
  binId?: string;
  sequence?: number;
}
export interface PickingListResponse {
  id: number;
  pickingListNumber?: string;
  warehouseLocationId?: string;
  companyId?: number;
  assignedUserId?: string;
  assignedAt?: string;
  status: string;
  items: PickingItemDto[];
}
export interface PackingVerifyResponse {
  valid: boolean;
  message?: string;
}
export interface CloseBoxResponse {
  ssccNumber: string;
  pickingListId: string;
}
export interface ShipmentResponse {
  id: number;
  shipmentNumber: string;
  status: string;
}

// ---------------------------------------------------------------------------
// Endpoint fonksiyonları
// ---------------------------------------------------------------------------

export const localizationService = {
  getLanguages: (all = false) =>
    localizationApi
      .get<Language[]>("/api/v1/languages", { params: all ? { all: true } : {} })
      .then((r) => r.data),
  addLanguage: (code: string, name: string) =>
    localizationApi.post<Language>("/api/v1/languages", { code, name }).then((r) => r.data),
  deactivateLanguage: (code: string) =>
    localizationApi.delete<Language>(`/api/v1/languages/${code}`).then((r) => r.data),
  autoTranslate: (code: string, force = false, source?: string) =>
    localizationApi
      .post<{ language: string; translatedKeys: number; async: boolean; source: string }>(
        `/api/v1/languages/${code}/auto-translate`,
        null,
        { params: { force, ...(source ? { source } : {}) } },
      )
      .then((r) => r.data),
  getTranslationStatus: (code: string) =>
    localizationApi
      .get<{ language: string; uiKeyCount: number; expectedKeys: number; missingKeys: number; ready: boolean }>(
        `/api/v1/languages/${code}/translation-status`,
      )
      .then((r) => r.data),
  reportMissingKeys: (locale: string, keyCodes: string[]) =>
    localizationApi
      .post(`/api/v1/translations/missing`, keyCodes, { params: { locale } })
      .then(() => undefined),
  getMissingTranslations: (locale?: string, page = 0, size = 50) =>
    localizationApi
      .get<{ items: unknown[]; total: number; unresolvedCount: number }>("/api/v1/translations/missing", {
        params: { locale, page, size },
      })
      .then((r) => r.data),
  syncTranslationKeys: (
    keys: { keyCode: string; module?: string; description?: string; tr?: string; en?: string }[],
  ) =>
    localizationApi.post<{ syncedKeys: number; async: boolean }>("/api/v1/translation-keys/sync", keys).then((r) => r.data),
  getTranslations: (locale: string, module = "UI") =>
    localizationApi
      .get<Record<string, string>>("/api/v1/translations", { params: { locale, module } })
      .then((r) => r.data),
  importTranslations: (lang: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return localizationApi
      .post<ImportResultDto>(`/api/v1/translations/import`, form, {
        params: { lang },
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => r.data);
  },
  exportTranslations: (lang: string, format: "xlsx" | "json") =>
    localizationApi
      .get<Blob>("/api/v1/translations/export", { params: { lang, format }, responseType: "blob" })
      .then((r) => r.data),
  getActiveFormat: () =>
    localizationApi.get<ActiveFormatResponse>("/api/v1/formats/active").then((r) => r.data),
  createAddress: (dto: AddressPayload) =>
    localizationApi.post<AddressResponse>("/api/addresses", dto).then((r) => r.data),
  updateAddress: (id: number, dto: AddressPayload) =>
    localizationApi.put<AddressResponse>(`/api/addresses/${id}`, dto).then((r) => r.data),
  getAddress: (id: number) =>
    localizationApi.get<AddressResponse>(`/api/addresses/${id}`).then((r) => r.data),
  listAddresses: (params?: { countryId?: number; page?: number; size?: number }) =>
    localizationApi
      .get<PageResp<AddressResponse>>("/api/addresses", { params })
      .then((r) => r.data),
  getCountryTemplate: (countryId: number) =>
    localizationApi
      .get<CountryAddressTemplateDto[]>(`/api/addresses/templates/${countryId}`)
      .then((r) => r.data),
};

// Localization — adres şablonu admin (İş İsteri 17)
export interface AddressTemplateFieldDto {
  id: number;
  fieldKey: string;
  fieldLabelKey: string;
  fieldType: "TEXT" | "MASTER_SELECT" | "FIXED";
  masterDataSource: "NONE" | "STATE" | "CITY" | "DISTRICT" | "NEIGHBORHOOD";
  parentFieldKey?: string | null;
}
export interface TemplateFieldMutationResponse {
  template: CountryAddressTemplateDto;
  warning?: string | null;
}
export interface CopyTemplateResultDto {
  countryId: number;
  sourceCountryId: number;
  copiedCount: number;
}

export const addressTemplateAdminService = {
  listFields: () =>
    localizationApi.get<AddressTemplateFieldDto[]>("/api/addresses/template-fields").then((r) => r.data),
  createField: (req: {
    fieldKey: string;
    fieldLabelKey: string;
    fieldType: string;
    masterDataSource: string;
    parentFieldKey?: string | null;
  }) => localizationApi.post<AddressTemplateFieldDto>("/api/addresses/template-fields", req).then((r) => r.data),
  updateField: (
    fieldId: number,
    req: { fieldLabelKey: string; fieldType: string; masterDataSource: string; parentFieldKey?: string | null },
  ) => localizationApi.put<AddressTemplateFieldDto>(`/api/addresses/template-fields/${fieldId}`, req).then((r) => r.data),
  deleteField: (fieldId: number) => localizationApi.delete(`/api/addresses/template-fields/${fieldId}`),
  addTemplateField: (
    countryId: number,
    req: { fieldId: number; mandatory?: boolean; sequence?: number; validationRegex?: string | null; errorMessageKey?: string | null },
  ) =>
    localizationApi
      .post<TemplateFieldMutationResponse>(`/api/addresses/templates/${countryId}/fields`, req)
      .then((r) => r.data),
  updateTemplateField: (
    countryId: number,
    templateId: number,
    req: { mandatory?: boolean; validationRegex?: string | null; errorMessageKey?: string | null },
  ) =>
    localizationApi
      .put<TemplateFieldMutationResponse>(`/api/addresses/templates/${countryId}/fields/${templateId}`, req)
      .then((r) => r.data),
  reorderTemplate: (countryId: number, entries: { templateId: number; sequence: number }[]) =>
    localizationApi
      .put<CountryAddressTemplateDto[]>(`/api/addresses/templates/${countryId}/reorder`, { entries })
      .then((r) => r.data),
  removeTemplateField: (countryId: number, templateId: number) =>
    localizationApi.delete(`/api/addresses/templates/${countryId}/fields/${templateId}`),
  copyFrom: (countryId: number, sourceCountryId: number) =>
    localizationApi
      .post<CopyTemplateResultDto>(`/api/addresses/templates/${countryId}/copy-from/${sourceCountryId}`)
      .then((r) => r.data),
};

export const addressMasterService = {
  countries: () => coreApi.get<CountryDto[]>("/api/address/countries").then((r) => r.data),
  states: (countryId: number) =>
    coreApi.get<StateProvinceDto[]>("/api/address/states", { params: { countryId } }).then((r) => r.data),
  cities: (countryId: number, stateId?: string) =>
    coreApi.get<NamedDto[]>("/api/address/cities", { params: { countryId, stateId } }).then((r) => r.data),
  districts: (cityId: string) =>
    coreApi.get<NamedDto[]>("/api/address/districts", { params: { cityId } }).then((r) => r.data),
  neighborhoods: (districtId: string) =>
    coreApi.get<NeighborhoodDto[]>("/api/address/neighborhoods", { params: { districtId } }).then((r) => r.data),
};

export const dynamicUiService = {
  getSchema: (screenCode: string, opts?: { roleId?: number; countryId?: number; operationType?: string }) =>
    coreApi
      .get<ResolvedScreenDto>(`/api/ui/screens/${screenCode}/schema`, { params: opts })
      .then((r) => r.data),
  getTableSchema: (screenCode: string, opts?: { roleId?: number; countryId?: number }) =>
    coreApi
      .get<ResolvedTableSchemaDto>(`/api/ui/screens/${screenCode}/table-schema`, { params: opts })
      .then((r) => r.data),
  putTablePreferences: (screenCode: string, columns: ColumnPreference[]) =>
    coreApi
      .put<void>(`/api/ui/screens/${screenCode}/table-preferences`, { columns })
      .then((r) => r.data),
  deleteTablePreferences: (screenCode: string) =>
    coreApi.delete<void>(`/api/ui/screens/${screenCode}/table-preferences`).then((r) => r.data),
  listColumnDefs: (screenCode: string) =>
    coreApi.get<ColumnDefResponse[]>(`/api/ui/screens/${screenCode}/columns`).then((r) => r.data),
  createColumnDef: (screenCode: string, req: UpsertColumnDefRequest) =>
    coreApi.post<ColumnDefResponse>(`/api/ui/screens/${screenCode}/columns`, req).then((r) => r.data),
  updateColumnDef: (screenCode: string, columnDefId: number, req: UpsertColumnDefRequest) =>
    coreApi
      .put<ColumnDefResponse>(`/api/ui/screens/${screenCode}/columns/${columnDefId}`, req)
      .then((r) => r.data),
  deleteColumnDef: (screenCode: string, columnDefId: number) =>
    coreApi.delete<void>(`/api/ui/screens/${screenCode}/columns/${columnDefId}`),
  listColumnRules: (screenCode: string) =>
    coreApi.get<ColumnRuleResponse[]>("/api/ui/column-rules", { params: { screenCode } }).then((r) => r.data),
  createColumnRule: (req: UpsertColumnRuleRequest) =>
    coreApi.post<ColumnRuleResponse>("/api/ui/column-rules", req).then((r) => r.data),
  deleteColumnRule: (ruleId: number) => coreApi.delete<void>(`/api/ui/column-rules/${ruleId}`),
  createRule: (req: UpsertRuleRequest) =>
    coreApi.post<RuleResponse>("/api/ui/rules", req).then((r) => r.data),
  updateRule: (ruleId: string, req: UpsertRuleRequest) =>
    coreApi.put<RuleResponse>(`/api/ui/rules/${ruleId}`, req).then((r) => r.data),
  deleteRule: (ruleId: number) => coreApi.delete<void>(`/api/ui/rules/${ruleId}`),
};

export const orgService = {
  getCompanies: () => coreApi.get<CompanySummaryDto[]>("/api/org/companies").then((r) => r.data),
  getLocations: (companyId: number) =>
    coreApi.get<LocationSummaryDto[]>(`/api/org/companies/${companyId}/locations`).then((r) => r.data),
  getLocation: (companyId: number, locationId: number) =>
    coreApi
      .get<LocationDetailDto>(`/api/org/companies/${companyId}/locations/${locationId}`)
      .then((r) => r.data),
  createLocation: (companyId: number, body: CreateLocationRequest) =>
    coreApi
      .post<LocationDetailDto>(`/api/org/companies/${companyId}/locations`, body)
      .then((r) => r.data),
  updateLocation: (companyId: number, locationId: number, body: UpdateLocationRequest) =>
    coreApi
      .put<LocationDetailDto>(`/api/org/companies/${companyId}/locations/${locationId}`, body)
      .then((r) => r.data),
  deactivateLocation: (companyId: number, locationId: number) =>
    coreApi
      .patch<LocationDetailDto>(`/api/org/companies/${companyId}/locations/${locationId}/deactivate`)
      .then((r) => r.data),
  getRegions: (countryId: number) =>
    coreApi.get<RegionSummaryDto[]>("/api/org/regions", { params: { countryId } }).then((r) => r.data),
};

export const processConfigService = {
  listSteps: (locationId: number, processCode?: string) =>
    coreApi
      .get<StepConfig[]>("/api/process-config/steps", { params: { locationId, processCode } })
      .then((r) => r.data),
  getStep: (stepConfigId: number) =>
    coreApi.get<StepConfig>(`/api/process-config/steps/${stepConfigId}`).then((r) => r.data),
  updateStep: (stepConfigId: number, req: UpdateStepConfigRequest) =>
    coreApi.put<StepConfig>(`/api/process-config/steps/${stepConfigId}`, req).then((r) => r.data),
};

export const approvalService = {
  listPending: () => coreApi.get<ApprovalRequestDto[]>("/api/approvals/pending").then((r) => r.data),
  approve: (id: number, note?: string) =>
    coreApi.post<ApprovalRequestDto>(`/api/approvals/${id}/approve`, null, { params: { note } }).then((r) => r.data),
  reject: (id: number, note?: string) =>
    coreApi.post<ApprovalRequestDto>(`/api/approvals/${id}/reject`, null, { params: { note } }).then((r) => r.data),
};

export const integrationService = {
  logs: (filter: { status?: string; locationId?: number; jobCode?: string; page?: number; size?: number }) =>
    integrationApi
      .get<PageResp<IntegrationLogResponse>>("/api/integrations/logs", {
        params: { sort: "createdAt,desc", size: 20, ...filter },
      })
      .then((r) => r.data),
  logDetail: (logId: number) =>
    integrationApi.get<IntegrationLogResponse>(`/api/integrations/logs/${logId}`).then((r) => r.data),
  retry: (logId: number) =>
    integrationApi.post<RetryResponse>(`/api/integrations/logs/${logId}/retry`).then((r) => r.data),
  enqueueTestMovement: (companyId: number, locationId: number) =>
    integrationApi
      .post<{ status: string; message: string }>(
        "/api/integrations/movements",
        {
          movementId: Date.now() % 1_000_000,
          movementType: "TRANSFER",
          sku: "SKU-DEMO",
          quantity: 1,
          unit: "EA",
          movementDate: new Date().toISOString(),
          companyId,
          locationId,
          referenceDocumentNo: `RT-${Date.now()}`,
        },
        { params: { locationId } },
      )
      .then((r) => r.data),
  stats: () =>
    integrationApi
      .get<{ since: string; counts: Record<string, number> }>("/api/integrations/stats")
      .then((r) => r.data),
};

export const financeService = {
  lookupRate: (fromCurrency: string, toCurrency: string, rateDate: string, rateType: RateType) =>
    financeApi
      .get<ExchangeRateDto>("/api/rates/lookup", { params: { fromCurrency, toCurrency, rateDate, rateType } })
      .then((r) => r.data),
  listActiveRates: (params?: { baseCurrency?: string; rateDate?: string; rateType?: RateType }) =>
    financeApi
      .get<ActiveRateListDto>("/api/rates/active", { params })
      .then((r) => r.data),
  syncTcmbRates: () =>
    financeApi.post<TcmbSyncResponse>("/api/rates/sync/tcmb").then((r) => r.data),
  upsertManualRate: (req: ManualRateRequest) =>
    financeApi.post<ManualRateResponse>("/api/rates/manual", req).then((r) => r.data),
  listTaxRates: (params?: { countryId?: number; locationId?: number; taxTypeCode?: string; active?: boolean; page?: number; size?: number }) =>
    financeApi
      .get<PageResp<TaxRateResponse>>("/api/taxes/rates", { params: { size: 50, ...params } })
      .then((r) => r.data),
  calculateTax: (req: TaxCalculateRequest) =>
    financeApi.post<TaxCalculateResponse>("/api/taxes/calculate", req).then((r) => r.data),
  updateTaxRate: (req: { taxRateId: number; newRate: number; effectiveDate: string }) =>
    financeApi.post<TaxRateVersionResult>("/api/taxes/rates/update", req).then((r) => r.data),
  createOrder: (req: OrderRequest) =>
    financeApi.post<OrderResponse>("/api/finance/orders", req).then((r) => r.data),
  createContract: (req: Record<string, unknown>) =>
    financeApi.post<ContractDto>("/api/finance/contracts", req).then((r) => r.data),
  recordTransaction: (req: Record<string, unknown>) =>
    financeApi.post<FinancialTransactionDto>("/api/finance/transactions", req).then((r) => r.data),
};

// Billing — çoklu para birimi fatura (wms-billing-service)
export type InvoiceStatus = "DRAFT" | "APPROVED" | "SENT_TO_ERP" | "CANCELLED";

export interface InvoiceItemInput {
  itemDescription: string;
  quantity: number;
  unitPriceOriginal: number;
  discountOriginal: number;
  taxRate: number;
}

export interface InvoiceItemResult extends InvoiceItemInput {
  lineTotalOriginal: number;
  taxAmountOriginal: number;
}

export interface InvoiceResponse {
  id: number | null;
  invoiceNumber: string | null;
  customerId: number;
  locationId: number;
  issueDate: string | null;
  createdAt: string | null;
  invoiceCurrency: string;
  accountingCurrency: string;
  exchangeRateDate: string;
  exchangeRateValue: number;
  items: InvoiceItemResult[];
  subtotalOriginal: number;
  taxAmountOriginal: number;
  grandTotalOriginal: number;
  grandTotalAccounting: number;
  status: InvoiceStatus;
  preview: boolean;
}

export interface CreateInvoiceRequest {
  customerId: number;
  invoiceCurrency: string;
  exchangeRateDate: string;
  items: InvoiceItemInput[];
}

export interface ExchangeDifferenceRequest {
  paidAmountOriginal: number;
  rateAtPayment: number;
}

export interface ExchangeDifferenceResponse {
  id: number;
  invoiceId: number;
  calculationDate: string;
  originalPaidAmount: number;
  rateAtPayment: number;
  exchangeDifferenceAmount: number;
  actionTaken: string;
}

export const billingService = {
  calculateInvoice: (req: CreateInvoiceRequest) =>
    billingApi.post<InvoiceResponse>("/api/billing/invoices/calculate", req).then((r) => r.data),
  createInvoice: (req: CreateInvoiceRequest) =>
    billingApi.post<InvoiceResponse>("/api/billing/invoices", req).then((r) => r.data),
  getInvoice: (id: number) =>
    billingApi.get<InvoiceResponse>(`/api/billing/invoices/${id}`).then((r) => r.data),
  listInvoices: (params?: { customerId?: number; status?: InvoiceStatus; page?: number; size?: number }) =>
    billingApi
      .get<PageResp<InvoiceResponse>>("/api/billing/invoices", { params: { size: 20, ...params } })
      .then((r) => r.data),
  updateDraft: (id: number, req: CreateInvoiceRequest) =>
    billingApi.put<InvoiceResponse>(`/api/billing/invoices/${id}`, req).then((r) => r.data),
  approveInvoice: (id: number) =>
    billingApi.post<InvoiceResponse>(`/api/billing/invoices/${id}/approve`).then((r) => r.data),
  cancelInvoice: (id: number) =>
    billingApi.post<InvoiceResponse>(`/api/billing/invoices/${id}/cancel`).then((r) => r.data),
  recordExchangeDifference: (invoiceId: number, req: ExchangeDifferenceRequest) =>
    billingApi
      .post<ExchangeDifferenceResponse>(`/api/billing/invoices/${invoiceId}/exchange-difference`, req)
      .then((r) => r.data),
};

// Core — depo lokasyonları (StorageLocationController)
export interface StorageLocationResponse {
  id: number;
  zoneId?: string;
  locationId?: number;
  zoneCode?: string;
  zoneType?: string;
  addressCode?: string;
  aisle?: string;
  bay?: string;
  shelf?: string;
  bin?: string;
  maxVolume?: number;
  maxWeight?: number;
  currentVolume?: number;
  currentWeight?: number;
  volumeUtilizationPercent?: number;
  active: boolean;
}

export const storageLocationService = {
  search: (params: { zoneId?: string; status?: string; aisle?: string; isActive?: boolean; page?: number; size?: number }) =>
    coreApi
      .get<PageResp<StorageLocationResponse>>("/api/locations/search", { params: { size: 20, ...params } })
      .then((r) => r.data),
  utilization: (thresholdPercent = 80) =>
    coreApi
      .get<StorageLocationResponse[]>("/api/locations/utilization", { params: { thresholdPercent } })
      .then((r) => r.data),
};

export const auditService = {
  logs: (params: { page?: number; size?: number; entityName?: string }) =>
    coreApi
      .get<PageResp<AuditLogEntry>>("/api/audit/logs", {
        params: { sort: "changedAt,desc", size: 20, ...params },
      })
      .then((r) => r.data),
};

export const inboundService = {
  listReceipts: (params?: { status?: string; page?: number; size?: number }) =>
    inboundApi
      .get<PageResp<ReceiptResponse>>("/api/inbound/receipts", { params: { size: 20, ...params } })
      .then((r) => r.data),
  startReceipt: (req: { inboundOrderId: string; receiptNumber: string; receivedByUserId: string }) =>
    inboundApi.post<ReceiptResponse>("/api/inbound/receipts", req).then((r) => r.data),
  submitQc: (receiptId: string, req: Record<string, unknown>) =>
    inboundApi.post<ReceiptResponse>(`/api/inbound/receipts/${receiptId}/qc`, req).then((r) => r.data),
  approveReceipt: (receiptId: string) =>
    inboundApi.post<ReceiptResponse>(`/api/inbound/receipts/${receiptId}/approve`).then((r) => r.data),
  getPutawayRecommendations: (receiptId: string) =>
    inboundApi.get<PutawayRecommendation[]>(`/api/inbound/receipts/${receiptId}/putaway`).then((r) => r.data),
};

export const inventoryService = {
  getStocks: () =>
    coreApi.get<{ stocks: StockItemDto[]; count: number }>("/api/stocks").then((r) => r.data),
  moveStock: (req: InternalMoveRequest) =>
    inventoryApi.post<void>("/api/inventory/move", req).then((r) => r.data),
  issueStock: (req: { shipmentNumber: string; items: { productCode: string; quantity: number; binId: string }[] }) =>
    inventoryApi.post<void>("/api/inventory/issue", req).then((r) => r.data),
};

export const outboundService = {
  listPickingLists: (params?: { page?: number; size?: number }) =>
    outboundApi
      .get<PageResp<PickingListResponse>>("/api/picking/lists", { params: { size: 20, ...params } })
      .then((r) => r.data),
  createPickingList: (req: { outboundOrderIds: string[]; warehouseLocationId: number; createdByUserId: string }) =>
    outboundApi.post<PickingListResponse>("/api/picking/lists", req).then((r) => r.data),
  listMyTasks: () => outboundApi.get<PickingListResponse[]>("/api/picking/tasks/my").then((r) => r.data),
  listUnassignedTasks: () =>
    outboundApi.get<PickingListResponse[]>("/api/picking/tasks/unassigned").then((r) => r.data),
  assignPickingList: (id: number, assignedUserId: string) =>
    outboundApi
      .post<PickingListResponse>(`/api/picking/lists/${id}/assign`, { assignedUserId })
      .then((r) => r.data),
  startPickingList: (id: number) =>
    outboundApi.post<PickingListResponse>(`/api/picking/lists/${id}/start`).then((r) => r.data),
  confirmPick: (itemId: number, pickedQty: number) =>
    outboundApi
      .post<PickingListResponse>(`/api/picking/items/${itemId}/confirm`, { pickedQty })
      .then((r) => r.data),
  verifyPacking: (req: Record<string, unknown>) =>
    outboundApi.post<PackingVerifyResponse>("/api/packing/verify", req).then((r) => r.data),
  closeBox: (pickingListId: string) =>
    outboundApi.post<CloseBoxResponse>("/api/packing/close-box", { pickingListId }).then((r) => r.data),
  listShipments: (params?: { page?: number; size?: number }) =>
    outboundApi
      .get<PageResp<ShipmentResponse>>("/api/shipping/shipments", { params: { size: 20, ...params } })
      .then((r) => r.data),
  createShipment: (req: Record<string, unknown>) =>
    outboundApi.post<ShipmentResponse>("/api/shipping/shipments", req).then((r) => r.data),
  verifyLoad: (shipmentId: string, boxSsccNumber: string) =>
    outboundApi
      .post<{ valid: boolean; message?: string }>("/api/shipping/verify-load", { shipmentId, boxSsccNumber })
      .then((r) => r.data),
  dispatch: (shipmentId: string) =>
    outboundApi.post<ShipmentResponse>(`/api/shipping/${shipmentId}/dispatch`).then((r) => r.data),
};

// Core — kullanıcı yönetimi (WMS_ADMIN)
export interface UserAccessDto {
  id: number;
  companyId: number;
  companyName: string;
  locationId?: number | null;
  locationName?: string | null;
  roleId: string;
  roleName: string;
}
export interface UserSummaryDto {
  id: number;
  username: string;
  email: string;
  keycloakUserId?: string | null;
  active: boolean;
  accesses: UserAccessDto[];
}
export interface RoleSummaryDto { id: number; name: string; }
export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  keycloakRoles: string[];
  accesses: { companyId: number; locationId?: number | null; roleId: number }[];
}

export const userService = {
  list: () => coreApi.get<UserSummaryDto[]>("/api/users").then((r) => r.data),
  create: (req: CreateUserRequest) => coreApi.post<UserSummaryDto>("/api/users", req).then((r) => r.data),
  deactivate: (userId: number) => coreApi.delete(`/api/users/${userId}`),
  listRoles: () => coreApi.get<RoleSummaryDto[]>("/api/users/roles").then((r) => r.data),
  listKeycloakRoles: () => coreApi.get<string[]>("/api/users/keycloak-roles").then((r) => r.data),
};

// Core — ülke ve idari birim yönetimi (İş İsteri 18, WMS_ADMIN)
export interface CountryAdminDto {
  id: number;
  isoCode: string;
  name: string;
  active: boolean;
  stateCount: number;
  cityCount: number;
}
export interface CountryUsageDto {
  countryId: number;
  addressCount: number | null;
  templateExists: boolean | null;
  checkAvailable: boolean;
}
export interface StateImportResultDto {
  imported: number;
  skipped: number;
  errors: string[];
}

export const geoAdminService = {
  listCountries: () => coreApi.get<CountryAdminDto[]>("/api/admin/geo/countries").then((r) => r.data),
  createCountry: (req: { isoCode: string; name: string }) =>
    coreApi.post<CountryAdminDto>("/api/admin/geo/countries", req).then((r) => r.data),
  updateCountry: (countryId: number, req: { name: string }) =>
    coreApi.put<CountryAdminDto>(`/api/admin/geo/countries/${countryId}`, req).then((r) => r.data),
  deactivateCountry: (countryId: number) => coreApi.delete<void>(`/api/admin/geo/countries/${countryId}`),
  reactivateCountry: (countryId: number) =>
    coreApi.post<CountryAdminDto>(`/api/admin/geo/countries/${countryId}/reactivate`).then((r) => r.data),
  getCountryUsage: (countryId: number) =>
    coreApi.get<CountryUsageDto>(`/api/admin/geo/countries/${countryId}/usage`).then((r) => r.data),
  addState: (countryId: number, req: { name: string; code?: string | null }) =>
    coreApi.post<StateProvinceDto>(`/api/admin/geo/countries/${countryId}/states`, req).then((r) => r.data),
  updateState: (stateId: number, req: { name: string; code?: string | null }) =>
    coreApi.put<StateProvinceDto>(`/api/admin/geo/states/${stateId}`, req).then((r) => r.data),
  deactivateState: (stateId: number) => coreApi.delete<void>(`/api/admin/geo/states/${stateId}`),
  importStates: (countryId: number, csv: string) =>
    coreApi
      .post<StateImportResultDto>(`/api/admin/geo/countries/${countryId}/states/import`, csv, {
        headers: { "Content-Type": "text/csv" },
      })
      .then((r) => r.data),
  addCity: (countryId: number, req: { name: string; stateProvinceId?: number | null }) =>
    coreApi.post<NamedDto>(`/api/admin/geo/countries/${countryId}/cities`, req).then((r) => r.data),
  assignCityToState: (cityId: number, stateProvinceId: number) =>
    coreApi.put<NamedDto>(`/api/admin/geo/cities/${cityId}/assign-state`, { stateProvinceId }).then((r) => r.data),
  addDistrict: (cityId: number, req: { name: string }) =>
    coreApi.post<NamedDto>(`/api/admin/geo/cities/${cityId}/districts`, req).then((r) => r.data),
  addNeighborhood: (districtId: number, req: { name: string; zipCode?: string | null }) =>
    coreApi.post<NeighborhoodDto>(`/api/admin/geo/districts/${districtId}/neighborhoods`, req).then((r) => r.data),
};
