/**
 * İş İsteri 17 — Ülke Bazlı Adres Yapısı Konfigürasyon Ekranı
 *
 * Sol panel: ülke şablonu düzenleme (sıra, mandatory, regex, errorKey).
 * Sağ panel: DynamicAddressField ile canlı önizleme.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Copy, Plus, Save, Trash2 } from "lucide-react";
import {
  addressMasterService,
  addressTemplateAdminService,
  describeError,
  localizationService,
  type AddressTemplateFieldDto,
  type CountryAddressTemplateDto,
  type CountryDto,
} from "../api/services";
import { DynamicAddressField } from "../components/DynamicAddressField";
import { DataTable } from "../components/DataTable";
import { ErrorBanner, Field, InfoBanner, Loading, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";
import { fallbackCol } from "../components/tableUtils";
import { useI18n } from "../i18n/I18nContext";

const PRESELECT_COUNTRY_KEY = "wms:template-config-country-id";
const smallBtn = { padding: "4px 10px", fontSize: "0.8rem" } as const;

const ADDRESS_TEMPLATE_FIELD_FALLBACK = [
  fallbackCol("sequence", "columns.common.sequence", 0, { dataType: "NUMBER" }),
  fallbackCol("fieldKey", "columns.template.fieldKey", 1),
  fallbackCol("mandatory", "columns.template.mandatory", 2, { dataType: "BOOLEAN" }),
  fallbackCol("regex", "columns.template.regex", 3),
  fallbackCol("errorKey", "columns.template.errorKey", 4),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true }),
];

function testRegex(pattern: string, sample: string): boolean | null {
  if (!pattern.trim()) return null;
  try {
    return new RegExp(pattern).test(sample);
  } catch {
    return false;
  }
}

export function AddressTemplateConfig() {
  const { t } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);

  const [countries, setCountries] = useState<CountryDto[]>([]);
  const [catalog, setCatalog] = useState<AddressTemplateFieldDto[]>([]);
  const [countryId, setCountryId] = useState<number | "">("");
  const [template, setTemplate] = useState<CountryAddressTemplateDto[]>([]);
  const [loading, setLoading] = useState(false);

  const [addFieldId, setAddFieldId] = useState<number | "">("");
  const [copySourceId, setCopySourceId] = useState<number | "">("");
  const [regexSamples, setRegexSamples] = useState<Record<string, string>>({});
  const [previewValues, setPreviewValues] = useState<Record<string, string>>({});

  const [newFieldOpen, setNewFieldOpen] = useState(false);
  const [newFieldKey, setNewFieldKey] = useState("");
  const [newFieldLabel, setNewFieldLabel] = useState("");
  const [newFieldType, setNewFieldType] = useState<"TEXT" | "MASTER_SELECT" | "FIXED">("TEXT");
  const [newFieldSource, setNewFieldSource] = useState<"NONE" | "STATE" | "CITY" | "DISTRICT" | "NEIGHBORHOOD">("NONE");
  const [newFieldParent, setNewFieldParent] = useState("");

  const sortedTemplate = useMemo(
    () => [...template].sort((a, b) => a.sequence - b.sequence),
    [template],
  );

  const availableCatalog = useMemo(() => {
    const used = new Set(template.map((t) => t.fieldKey));
    return catalog.filter((f) => !used.has(f.fieldKey));
  }, [catalog, template]);

  const loadCatalog = useCallback(() => {
    addressTemplateAdminService.listFields().then(setCatalog).catch((e) => setError(describeError(e)));
  }, []);

  const loadTemplate = useCallback((id: number) => {
    setLoading(true);
    localizationService
      .getCountryTemplate(id)
      .then((rows) => {
        setTemplate(rows);
        setPreviewValues({});
      })
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    addressMasterService.countries().then(setCountries).catch((e) => setError(describeError(e)));
    loadCatalog();
    const pre = sessionStorage.getItem(PRESELECT_COUNTRY_KEY);
    if (pre) {
      const id = Number(pre);
      if (!Number.isNaN(id)) setCountryId(id);
      sessionStorage.removeItem(PRESELECT_COUNTRY_KEY);
    }
  }, [loadCatalog]);

  useEffect(() => {
    if (countryId !== "") loadTemplate(countryId);
    else setTemplate([]);
  }, [countryId, loadTemplate]);

  const notify = (msg: string) => {
    setSuccess(msg);
    setError(null);
  };

  const addFromCatalog = async () => {
    if (countryId === "" || addFieldId === "") return;
    try {
      const res = await addressTemplateAdminService.addTemplateField(countryId, { fieldId: addFieldId });
      setWarning(res.warning === "MASTER_DATA_EMPTY" ? t("tpl.masterDataEmpty") : null);
      notify(t("tpl.fieldAdded"));
      setAddFieldId("");
      loadTemplate(countryId);
    } catch (e) {
      setError(describeError(e));
    }
  };

  const createCatalogField = async () => {
    try {
      await addressTemplateAdminService.createField({
        fieldKey: newFieldKey.trim(),
        fieldLabelKey: newFieldLabel.trim(),
        fieldType: newFieldType,
        masterDataSource: newFieldSource,
        parentFieldKey: newFieldParent.trim() || null,
      });
      setNewFieldOpen(false);
      setNewFieldKey("");
      setNewFieldLabel("");
      notify(t("tpl.catalogCreated"));
      loadCatalog();
    } catch (e) {
      setError(describeError(e));
    }
  };

  const saveRow = async (row: CountryAddressTemplateDto) => {
    if (countryId === "" || row.id == null) return;
    try {
      await addressTemplateAdminService.updateTemplateField(countryId, row.id, {
        mandatory: row.mandatory,
        validationRegex: row.validationRegex ?? null,
        errorMessageKey: row.errorMessageKey ?? null,
      });
      notify(t("tpl.saved"));
    } catch (e) {
      setError(describeError(e));
    }
  };

  const removeRow = async (templateId: number) => {
    if (countryId === "") return;
    try {
      await addressTemplateAdminService.removeTemplateField(countryId, templateId);
      notify(t("tpl.removed"));
      loadTemplate(countryId);
    } catch (e) {
      setError(describeError(e));
    }
  };

  const moveRow = async (index: number, direction: -1 | 1) => {
    if (countryId === "") return;
    const next = index + direction;
    if (next < 0 || next >= sortedTemplate.length) return;
    const reordered = [...sortedTemplate];
    const tmp = reordered[index].sequence;
    reordered[index] = { ...reordered[index], sequence: reordered[next].sequence };
    reordered[next] = { ...reordered[next], sequence: tmp };
    try {
      const entries = reordered
        .filter((r) => r.id != null)
        .map((r, i) => ({ templateId: r.id!, sequence: i + 1 }));
      const updated = await addressTemplateAdminService.reorderTemplate(countryId, entries);
      setTemplate(updated);
    } catch (e) {
      setError(describeError(e));
    }
  };

  const copyTemplate = async () => {
    if (countryId === "" || copySourceId === "") return;
    try {
      const res = await addressTemplateAdminService.copyFrom(countryId, copySourceId);
      notify(t("tpl.copied").replace("{count}", String(res.copiedCount)));
      loadTemplate(countryId);
    } catch (e) {
      setError(describeError(e));
    }
  };

  const updateLocalRow = (templateId: number, patch: Partial<CountryAddressTemplateDto>) => {
    setTemplate((prev) => prev.map((r) => (r.id === templateId ? { ...r, ...patch } : r)));
  };

  const selectedCountry = countries.find((c) => c.id === countryId);

  return (
    <div>
      <PageHeader title={t("tpl.title")} subtitle={t("tpl.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />
      {warning && <InfoBanner message={warning} />}

      <Section title={t("tpl.countrySelect")}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
          <Field label={t("geo.name")}>
            <select
              className="form-select"
              style={{ minWidth: 220 }}
              value={countryId === "" ? "" : String(countryId)}
              onChange={(e) => setCountryId(e.target.value === "" ? "" : Number(e.target.value))}
            >
              <option value="">—</option>
              {countries.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.isoCode})
                </option>
              ))}
            </select>
          </Field>
          <Field label={t("tpl.copyFrom")}>
            <span style={{ display: "flex", gap: 8 }}>
              <select
                className="form-select"
                value={copySourceId === "" ? "" : String(copySourceId)}
                onChange={(e) => setCopySourceId(e.target.value === "" ? "" : Number(e.target.value))}
              >
                <option value="">—</option>
                {countries.filter((c) => c.id !== countryId).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              <button
                className="btn btn-secondary"
                disabled={countryId === "" || copySourceId === ""}
                onClick={() => void copyTemplate()}
              >
                <Copy size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                {t("tpl.copy")}
              </button>
            </span>
          </Field>
        </div>
      </Section>

      {countryId === "" ? (
        <InfoBanner message={t("tpl.selectCountryHint")} />
      ) : loading ? (
        <Loading />
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20, alignItems: "start" }}>
          {/* Sol — şablon editörü */}
          <div>
            <Section title={`${t("tpl.editor")} — ${selectedCountry?.name ?? ""}`}>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14, alignItems: "flex-end" }}>
                <Field label={t("tpl.addFromCatalog")}>
                  <select
                    className="form-select"
                    value={addFieldId === "" ? "" : String(addFieldId)}
                    onChange={(e) => setAddFieldId(e.target.value === "" ? "" : Number(e.target.value))}
                  >
                    <option value="">—</option>
                    {availableCatalog.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.fieldKey} ({f.fieldType})
                      </option>
                    ))}
                  </select>
                </Field>
                <button
                  className="btn btn-primary"
                  style={{ marginBottom: 14 }}
                  disabled={addFieldId === ""}
                  onClick={() => void addFromCatalog()}
                >
                  <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                  {t("tpl.addField")}
                </button>
                <button className="btn btn-secondary" style={{ marginBottom: 14 }} onClick={() => setNewFieldOpen((v) => !v)}>
                  {t("tpl.newCatalogField")}
                </button>
              </div>

              {newFieldOpen && (
                <div className="glass-card" style={{ padding: 14, marginBottom: 14 }}>
                  <div style={grid2}>
                    <Field label="field_key">
                      <input className="form-input" value={newFieldKey} onChange={(e) => setNewFieldKey(e.target.value)} />
                    </Field>
                    <Field label="field_label_key">
                      <input className="form-input" value={newFieldLabel} onChange={(e) => setNewFieldLabel(e.target.value)} />
                    </Field>
                    <Field label="field_type">
                      <select className="form-select" value={newFieldType} onChange={(e) => setNewFieldType(e.target.value as typeof newFieldType)}>
                        <option value="TEXT">TEXT</option>
                        <option value="MASTER_SELECT">MASTER_SELECT</option>
                        <option value="FIXED">FIXED</option>
                      </select>
                    </Field>
                    <Field label="master_data_source">
                      <select className="form-select" value={newFieldSource} onChange={(e) => setNewFieldSource(e.target.value as typeof newFieldSource)}>
                        <option value="NONE">NONE</option>
                        <option value="STATE">STATE</option>
                        <option value="CITY">CITY</option>
                        <option value="DISTRICT">DISTRICT</option>
                        <option value="NEIGHBORHOOD">NEIGHBORHOOD</option>
                      </select>
                    </Field>
                    <Field label="parent_field_key">
                      <input className="form-input" value={newFieldParent} onChange={(e) => setNewFieldParent(e.target.value)} placeholder="__country__" />
                    </Field>
                  </div>
                  <button className="btn btn-primary" disabled={!newFieldKey.trim() || !newFieldLabel.trim()} onClick={() => void createCatalogField()}>
                    {t("tpl.createCatalog")}
                  </button>
                </div>
              )}

              {sortedTemplate.length === 0 ? (
                <InfoBanner message={t("tpl.empty")} />
              ) : (
                <DataTable
                  screenCode="ADDRESS_TEMPLATE_FIELD_LIST"
                  rowKey={(row) => String(row.id ?? row.fieldKey)}
                  rows={sortedTemplate}
                  showColumnPicker={false}
                  fallbackColumns={ADDRESS_TEMPLATE_FIELD_FALLBACK}
                  renderers={{
                    sequence: (row) => {
                      const idx = sortedTemplate.findIndex((r) => (r.id ?? r.fieldKey) === (row.id ?? row.fieldKey));
                      return (
                        <span style={{ display: "inline-flex", gap: 4 }}>
                          <button className="btn btn-secondary" style={smallBtn} disabled={idx === 0} onClick={() => void moveRow(idx, -1)}>
                            <ArrowUp size={12} />
                          </button>
                          <button
                            className="btn btn-secondary"
                            style={smallBtn}
                            disabled={idx === sortedTemplate.length - 1}
                            onClick={() => void moveRow(idx, 1)}
                          >
                            <ArrowDown size={12} />
                          </button>
                          <span style={{ fontFamily: "monospace", marginLeft: 4 }}>{row.sequence}</span>
                        </span>
                      );
                    },
                    fieldKey: (row) => (
                      <>
                        <div style={{ fontWeight: 600 }}>{row.fieldKey}</div>
                        <div style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                          {row.fieldType} / {row.masterDataSource}
                        </div>
                      </>
                    ),
                    mandatory: (row) => (
                      <input
                        type="checkbox"
                        checked={row.mandatory}
                        onChange={(e) => row.id != null && updateLocalRow(row.id, { mandatory: e.target.checked })}
                      />
                    ),
                    regex: (row) => {
                      const sample = regexSamples[row.fieldKey] ?? "";
                      const regexOk = testRegex(row.validationRegex ?? "", sample);
                      return (
                        <>
                          <input
                            className="form-input"
                            style={{ padding: "4px 8px", fontSize: "0.8rem", width: 140 }}
                            value={row.validationRegex ?? ""}
                            onChange={(e) => row.id != null && updateLocalRow(row.id, { validationRegex: e.target.value })}
                          />
                          <input
                            className="form-input"
                            style={{ padding: "4px 8px", fontSize: "0.8rem", width: 100, marginTop: 4 }}
                            placeholder={t("tpl.regexTest")}
                            value={sample}
                            onChange={(e) => setRegexSamples((p) => ({ ...p, [row.fieldKey]: e.target.value }))}
                          />
                          {regexOk != null && (
                            <span style={{ fontSize: "0.75rem", color: regexOk ? "var(--neon-green)" : "var(--neon-red)" }}>
                              {regexOk ? "✓" : "✗"}
                            </span>
                          )}
                        </>
                      );
                    },
                    errorKey: (row) => (
                      <input
                        className="form-input"
                        style={{ padding: "4px 8px", fontSize: "0.8rem", width: 160 }}
                        value={row.errorMessageKey ?? ""}
                        onChange={(e) => row.id != null && updateLocalRow(row.id, { errorMessageKey: e.target.value })}
                      />
                    ),
                    actions: (row) => (
                      <span style={{ display: "inline-flex", gap: 4 }}>
                        <button className="btn btn-primary" style={smallBtn} disabled={row.id == null} onClick={() => void saveRow(row)}>
                          <Save size={12} />
                        </button>
                        <button className="btn btn-secondary" style={smallBtn} disabled={row.id == null} onClick={() => row.id != null && void removeRow(row.id)}>
                          <Trash2 size={12} />
                        </button>
                      </span>
                    ),
                  }}
                />
              )}
            </Section>
          </div>

          {/* Sağ — canlı önizleme */}
          <div>
            <Section title={t("tpl.preview")} style={{ marginBottom: 0 }}>
              <InfoBanner message={t("tpl.previewHint")} />
              <div style={{ marginTop: 12 }}>
                {sortedTemplate
                  .filter((tpl) => tpl.fieldType !== "FIXED")
                  .map((tpl) => (
                    <DynamicAddressField
                      key={tpl.fieldKey}
                      template={tpl}
                      value={previewValues[tpl.fieldKey] ?? ""}
                      onChange={(v) => setPreviewValues((p) => ({ ...p, [tpl.fieldKey]: v }))}
                    />
                  ))}
                {sortedTemplate.filter((t) => t.fieldType !== "FIXED").length === 0 && (
                  <InfoBanner message={t("tpl.previewEmpty")} />
                )}
              </div>
            </Section>
          </div>
        </div>
      )}
    </div>
  );
}

/** CountryManagement'tan deep-link için ülke id'sini session'a yazar. */
export function openAddressTemplateConfig(countryId: number) {
  sessionStorage.setItem(PRESELECT_COUNTRY_KEY, String(countryId));
  window.dispatchEvent(new CustomEvent("wms:navigate", { detail: { view: "address-template-config" } }));
}
