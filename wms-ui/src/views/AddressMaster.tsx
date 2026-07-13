/**
 * İş İsteri 10 — Yerel ve Uluslararası Adres Yapısı (generic/dinamik)
 *
 * Sabit kısım yalnızca ülke seçicisidir (core /api/address/countries).
 * Diğer tüm alanlar şablondan gelir ve serbest metin olarak girilir.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { MapPinned, Pencil, Save } from "lucide-react";
import {
  addressMasterService,
  describeError,
  localizationService,
  type AddressResponse,
  type CountryAddressTemplateDto,
  type CountryDto,
  type PageResp,
} from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { templateFieldError, templateFieldLabel } from "../i18n/templateText";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";
import { DataTable } from "../components/DataTable";
import { DynamicAddressField } from "../components/DynamicAddressField";
import { fallbackCol } from "../components/tableUtils";

const FALLBACK_COLUMNS = [
  fallbackCol("id", "columns.common.id", 0, { locked: true, dataType: "NUMBER" }),
  fallbackCol("country", "columns.address.country", 1),
  fallbackCol("formattedAddress", "columns.address.formatted", 2),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true, dataType: "CUSTOM" }),
];

function SelectField({ label, value, onChange, options, disabled }: {
  label: string;
  value: number | "";
  onChange: (v: number | "") => void;
  options: { id: number; name: string }[];
  disabled?: boolean;
}) {
  return (
    <Field label={label}>
      <select
        className="form-select"
        value={value === "" ? "" : String(value)}
        disabled={disabled}
        onChange={(e) => {
          const v = e.target.value;
          onChange(v === "" ? "" : Number(v));
        }}
      >
        <option value="">—</option>
        {options.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
      </select>
    </Field>
  );
}

function applyAddressToForm(
  addr: AddressResponse,
  template: CountryAddressTemplateDto[],
): { fieldValues: Record<string, string>; zip: string } {
  const fieldValues: Record<string, string> = {};
  if (addr.state) fieldValues.state = addr.state;
  if (addr.city) fieldValues.city = addr.city;

  const details = addr.addressDetails ?? {};
  template.forEach((tpl) => {
    if (tpl.fieldKey === "zip_code" || tpl.fieldType === "FIXED") return;
    const value = details[tpl.fieldKey];
    if (typeof value === "string") fieldValues[tpl.fieldKey] = value;
  });

  return { fieldValues, zip: addr.zipCode ?? "" };
}

export function AddressMaster() {
  const { t } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [countries, setCountries] = useState<CountryDto[]>([]);
  const [countryId, setCountryId] = useState<number | "">("");
  const [zip, setZip] = useState("");

  const [template, setTemplate] = useState<CountryAddressTemplateDto[]>([]);
  const [templateLoaded, setTemplateLoaded] = useState(false);
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [saved, setSaved] = useState<AddressResponse | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);

  const [listPage, setListPage] = useState<PageResp<AddressResponse> | null>(null);
  const [listPageNo, setListPageNo] = useState(0);
  const [listCountryFilter, setListCountryFilter] = useState<number | "">("");
  const [listLoading, setListLoading] = useState(false);

  const country = countries.find((c) => c.id === countryId);
  const iso = country?.isoCode?.toUpperCase() ?? "";

  const countryNameById = useMemo(() => {
    const map = new Map<number, string>();
    countries.forEach((c) => map.set(c.id, `${c.name} (${c.isoCode})`));
    return map;
  }, [countries]);

  useEffect(() => {
    addressMasterService
      .countries()
      .then(setCountries)
      .catch((e) => setError(describeError(e)));
  }, []);

  const loadList = useCallback(() => {
    setListLoading(true);
    localizationService
      .listAddresses({
        page: listPageNo,
        size: 20,
        ...(listCountryFilter !== "" ? { countryId: listCountryFilter } : {}),
      })
      .then(setListPage)
      .catch((e) => setError(describeError(e)))
      .finally(() => setListLoading(false));
  }, [listPageNo, listCountryFilter]);

  useEffect(() => {
    loadList();
  }, [loadList]);

  const resetDynamicFields = useCallback(() => {
    setFieldValues({});
    setFieldErrors({});
    setZip("");
  }, []);

  const loadTemplateForCountry = useCallback(async (id: number) => {
    const list = await localizationService.getCountryTemplate(id);
    const sorted = [...list].sort((a, b) => a.sequence - b.sequence);
    setTemplate(sorted);
    setTemplateLoaded(true);
    return sorted;
  }, []);

  const onCountry = (id: number | "") => {
    setCountryId(id);
    setTemplate([]);
    setTemplateLoaded(false);
    resetDynamicFields();
    if (!id) return;
    void loadTemplateForCountry(id).catch(() => {
      setTemplate([]);
      setTemplateLoaded(true);
    });
  };

  const loadAddressForEdit = async (addr: AddressResponse) => {
    setError(null);
    setSuccess(null);
    setEditingId(addr.id);
    setSaved(addr);
    setCountryId(addr.countryId);
    setTemplate([]);
    setTemplateLoaded(false);
    setFieldErrors({});

    try {
      const sorted = await loadTemplateForCountry(addr.countryId);
      const { fieldValues: values, zip: zipValue } = applyAddressToForm(addr, sorted);
      setFieldValues(values);
      setZip(zipValue);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (e) {
      setError(describeError(e));
    }
  };

  const onZipChange = (value: string) => {
    setZip(value);
    setFieldErrors((prev) => {
      if (!prev.zip_code) return prev;
      const next = { ...prev };
      delete next.zip_code;
      return next;
    });
  };

  const handleFieldChange = (fieldKey: string, value: string) => {
    setFieldErrors((prev) => {
      if (!prev[fieldKey]) return prev;
      const next = { ...prev };
      delete next[fieldKey];
      return next;
    });
    setFieldValues((prev) => ({ ...prev, [fieldKey]: value }));
  };

  const getFieldValue = useCallback(
    (fieldKey: string): string => {
      if (fieldKey === "zip_code") return zip;
      return fieldValues[fieldKey] ?? "";
    },
    [zip, fieldValues],
  );

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    template.forEach((tpl) => {
      const value = getFieldValue(tpl.fieldKey).trim();
      if (tpl.mandatory && !value) {
        errors[tpl.fieldKey] = templateFieldError(t, tpl, "required");
        return;
      }
      if (value && tpl.validationRegex) {
        try {
          const re = new RegExp(tpl.validationRegex);
          if (!re.test(value)) {
            errors[tpl.fieldKey] = templateFieldError(t, tpl, "invalid");
          }
        } catch {
          /* geçersiz regex */
        }
      }
    });
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const save = async () => {
    setError(null);
    setSuccess(null);
    if (!countryId) return;
    if (!validate()) {
      setError(t("addr.validationFailed"));
      return;
    }
    try {
      const addressDetails: Record<string, unknown> = { countryIso: iso };
      template.forEach((tpl) => {
        if (tpl.fieldType === "FIXED") return;
        const value = fieldValues[tpl.fieldKey];
        if (value) addressDetails[tpl.fieldKey] = value;
      });

      const payload = {
        countryId,
        state: fieldValues.state || undefined,
        city: fieldValues.city || undefined,
        zipCode: zip || undefined,
        addressDetails,
      };
      const wasEdit = Boolean(editingId);
      const result = wasEdit
        ? await localizationService.updateAddress(editingId!, payload)
        : await localizationService.createAddress(payload);
      setSaved(result);
      setEditingId(result.id);
      setSuccess(wasEdit ? t("addr.updated") : t("addr.saved"));
      loadList();
    } catch (e) {
      setError(describeError(e));
    }
  };

  const startNew = () => {
    setEditingId(null);
    setSaved(null);
    setSuccess(null);
    setError(null);
    setCountryId("");
    setTemplate([]);
    setTemplateLoaded(false);
    resetDynamicFields();
  };

  const zipPattern = useMemo(() => {
    const zipTpl = template.find((t) => t.fieldKey === "zip_code");
    if (!zipTpl?.validationRegex) return undefined;
    try {
      void new RegExp(zipTpl.validationRegex);
      return zipTpl.validationRegex;
    } catch {
      return undefined;
    }
  }, [template]);

  const renderTemplateField = (tpl: CountryAddressTemplateDto) => {
    const fieldError = fieldErrors[tpl.fieldKey];

    if (tpl.fieldType === "FIXED" && tpl.fieldKey === "zip_code") {
      return (
        <Field key="zip_code" label={templateFieldLabel(t, tpl)}>
          <input
            className="form-input"
            value={zip}
            pattern={zipPattern}
            disabled={!countryId}
            onChange={(e) => onZipChange(e.target.value)}
          />
          {fieldError && (
            <div style={{ fontSize: "0.78rem", color: "var(--neon-red)", marginTop: 4 }}>{fieldError}</div>
          )}
        </Field>
      );
    }

    return (
      <DynamicAddressField
        key={tpl.fieldKey}
        template={tpl}
        value={fieldValues[tpl.fieldKey] ?? ""}
        onChange={(value) => handleFieldChange(tpl.fieldKey, value)}
        disabled={!countryId}
        error={fieldError}
      />
    );
  };

  const canSave = Boolean(countryId);

  return (
    <div>
      <PageHeader title={t("addr.title")} subtitle={t("addr.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <Section title={editingId ? t("addr.update") : t("addr.title")}>
        <div style={grid2}>
          <SelectField
            label={t("addr.country")}
            value={countryId}
            onChange={onCountry}
            options={countries.map((c) => ({ id: c.id, name: `${c.name} (${c.isoCode})` }))}
          />

          {countryId && template.map(renderTemplateField)}
        </div>

        {countryId && templateLoaded && template.length === 0 && (
          <InfoBanner message={t("addr.noExtraFields")} />
        )}
        {!countryId && <InfoBanner message={t("addr.subtitle")} />}

        <button className="btn btn-primary" disabled={!canSave} onClick={() => void save()}>
          <Save size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
          {editingId ? t("addr.update") : t("addr.save")}
        </button>
        {editingId && (
          <button className="btn btn-secondary" style={{ marginLeft: 8 }} onClick={startNew}>
            {t("addr.new")}
          </button>
        )}
      </Section>

      {saved && (
        <Section title={t("addr.formatted")}>
          <div style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
            <MapPinned size={18} color="var(--neon-green)" style={{ marginTop: 2 }} />
            <div>
              <div style={{ color: "var(--text-primary)", fontSize: "1rem", marginBottom: 6 }}>
                {saved.formattedAddress ?? "—"}
              </div>
              <div style={{ fontFamily: "monospace", fontSize: "0.75rem", color: "var(--text-muted)" }}>id: {saved.id}</div>
            </div>
          </div>
        </Section>
      )}

      <Section title={t("addr.listTitle")}>
        <div style={{ display: "flex", gap: 12, marginBottom: 16, flexWrap: "wrap", alignItems: "flex-end" }}>
          <Field label={t("addr.country")}>
            <select
              className="form-select"
              style={{ minWidth: 220 }}
              value={listCountryFilter === "" ? "" : String(listCountryFilter)}
              onChange={(e) => {
                const v = e.target.value;
                setListPageNo(0);
                setListCountryFilter(v === "" ? "" : Number(v));
              }}
            >
              <option value="">{t("addr.filterCountry")}</option>
              {countries.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.isoCode})</option>
              ))}
            </select>
          </Field>
          <button className="btn btn-secondary" onClick={loadList}>{t("common.refresh")}</button>
        </div>

        <DataTable
          screenCode="ADDRESS_LIST"
          rowKey={(row) => row.id}
          rows={listPage?.content ?? []}
          loading={listLoading}
          fallbackColumns={FALLBACK_COLUMNS}
          emptyMessage={t("addr.listEmpty")}
          renderers={{
            id: (row) => (
              <span style={{ fontFamily: "monospace", fontSize: "0.85rem" }}>{row.id}</span>
            ),
            country: (row) => countryNameById.get(row.countryId) ?? row.countryId,
            formattedAddress: (row) => (
              <span style={{ maxWidth: 420, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "block" }}>
                {row.formattedAddress ?? "—"}
              </span>
            ),
            actions: (row) => (
              <button
                className="btn btn-secondary"
                style={{ padding: "4px 10px", fontSize: "0.8rem" }}
                onClick={() => void loadAddressForEdit(row)}
              >
                <Pencil size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                {t("addr.edit")}
              </button>
            ),
          }}
        />
        {listPage && !listLoading && listPage.content.length > 0 && (
          <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 14 }}>
            <button
              className="btn btn-secondary"
              disabled={listPageNo === 0}
              onClick={() => setListPageNo((p) => p - 1)}
            >
              ‹
            </button>
            <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem" }}>
              {listPageNo + 1} / {Math.max(listPage.totalPages, 1)} — {listPage.totalElements} kayıt
            </span>
            <button
              className="btn btn-secondary"
              disabled={listPageNo + 1 >= listPage.totalPages}
              onClick={() => setListPageNo((p) => p + 1)}
            >
              ›
            </button>
          </div>
        )}
      </Section>
    </div>
  );
}
