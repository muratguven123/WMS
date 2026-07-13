/**
 * İş İsteri 18 — UI Üzerinden Ülke ve İdari Birim Yönetimi
 *
 * Ülke CRUD (soft delete + reaktivasyon), eyalet/şehir/ilçe/mahalle
 * yönetimi, CSV toplu eyalet import'u ve pasifleştirme öncesi
 * cross-service kullanım onayı (usage check) içerir.
 *
 * Yalnızca WMS_ADMIN erişir (bkz. auth/roles.ts).
 */

import { useCallback, useEffect, useState } from "react";
import { Globe2, Plus, Power, RotateCcw, Upload } from "lucide-react";
import {
  addressMasterService,
  describeError,
  geoAdminService,
  type CountryAdminDto,
  type CountryUsageDto,
  type NamedDto,
  type StateImportResultDto,
  type StateProvinceDto,
} from "../api/services";
import { openAddressTemplateConfig } from "./AddressTemplateConfig";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const FALLBACK_COLUMNS = [
  fallbackCol("isoCode", "columns.country.iso", 0, { locked: true }),
  fallbackCol("name", "columns.country.name", 1),
  fallbackCol("stateCount", "columns.country.stateCount", 2, { dataType: "NUMBER" }),
  fallbackCol("cityCount", "columns.country.cityCount", 3, { dataType: "NUMBER" }),
  fallbackCol("status", "columns.common.status", 4),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true, dataType: "CUSTOM" }),
];

const smallBtn = { padding: "4px 10px", fontSize: "0.8rem" } as const;

export function CountryManagement() {
  const { t } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [countries, setCountries] = useState<CountryAdminDto[]>([]);
  const [loading, setLoading] = useState(false);

  // Yeni ülke formu
  const [newIso, setNewIso] = useState("");
  const [newName, setNewName] = useState("");

  // Satır içi ad düzenleme
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState("");

  // Pasifleştirme onayı
  const [confirmTarget, setConfirmTarget] = useState<CountryAdminDto | null>(null);
  const [usage, setUsage] = useState<CountryUsageDto | null>(null);

  // Seçili ülke (idari birim yönetimi)
  const [selected, setSelected] = useState<CountryAdminDto | null>(null);

  const notify = useCallback((msg: string) => {
    setSuccess(msg);
    setError(null);
  }, []);
  const fail = useCallback((e: unknown) => {
    setError(describeError(e));
    setSuccess(null);
  }, []);

  const loadCountries = useCallback(() => {
    setLoading(true);
    geoAdminService
      .listCountries()
      .then((data) => {
        if (!Array.isArray(data)) {
          setCountries([]);
          setError("Ülke listesi beklenen formatta değil (proxy/API yanıtını kontrol edin).");
          return;
        }
        setCountries(data);
      })
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadCountries();
  }, [loadCountries]);

  const createCountry = async () => {
    try {
      await geoAdminService.createCountry({ isoCode: newIso.trim(), name: newName.trim() });
      setNewIso("");
      setNewName("");
      notify(`${t("geo.created")} ${t("geo.templateLink")}`);
      loadCountries();
    } catch (e) {
      fail(e);
    }
  };

  const saveName = async (countryId: number) => {
    try {
      await geoAdminService.updateCountry(countryId, { name: editingName.trim() });
      setEditingId(null);
      notify(t("geo.updated"));
      loadCountries();
    } catch (e) {
      fail(e);
    }
  };

  /** Pasifleştirme akışı: önce usage çek, onay panelini göster (Teknik İş Kuralı 2). */
  const askDeactivate = async (country: CountryAdminDto) => {
    setConfirmTarget(country);
    setUsage(null);
    try {
      setUsage(await geoAdminService.getCountryUsage(country.id));
    } catch {
      setUsage({ countryId: country.id, addressCount: null, templateExists: null, checkAvailable: false });
    }
  };

  const confirmDeactivate = async () => {
    if (!confirmTarget) return;
    try {
      await geoAdminService.deactivateCountry(confirmTarget.id);
      if (selected?.id === confirmTarget.id) setSelected(null);
      setConfirmTarget(null);
      notify(t("geo.deactivated"));
      loadCountries();
    } catch (e) {
      fail(e);
    }
  };

  const reactivate = async (countryId: number) => {
    try {
      await geoAdminService.reactivateCountry(countryId);
      notify(t("geo.reactivated"));
      loadCountries();
    } catch (e) {
      fail(e);
    }
  };

  const usageMessage = (u: CountryUsageDto | null): string => {
    if (!u) return t("common.loading");
    if (!u.checkAvailable) return t("geo.usageUnknown");
    return t("geo.usageInfo")
      .replace("{count}", String(u.addressCount ?? 0))
      .replace("{template}", u.templateExists ? t("geo.templateYes") : t("geo.templateNo"));
  };

  return (
    <div>
      <PageHeader title={t("geo.title")} subtitle={t("geo.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      {/* ── Yeni Ülke ─────────────────────────────────────────────── */}
      <Section title={t("geo.newCountry")}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
          <Field label={`${t("geo.isoCode")} (${t("geo.isoHint")})`}>
            <input
              className="form-input"
              style={{ width: 160 }}
              value={newIso}
              maxLength={3}
              onChange={(e) => setNewIso(e.target.value.toUpperCase())}
            />
          </Field>
          <Field label={t("geo.name")}>
            <input className="form-input" value={newName} onChange={(e) => setNewName(e.target.value)} />
          </Field>
          <button
            className="btn btn-primary"
            style={{ marginBottom: 14 }}
            disabled={newIso.trim().length < 2 || !newName.trim()}
            onClick={() => void createCountry()}
          >
            <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("geo.create")}
          </button>
        </div>
      </Section>

      {/* ── Pasifleştirme onayı ───────────────────────────────────── */}
      {confirmTarget && (
        <Section title={`${t("geo.confirmTitle")} — ${confirmTarget.name} (${confirmTarget.isoCode})`}>
          <InfoBanner message={usageMessage(usage)} />
          <div style={{ display: "flex", gap: 10 }}>
            <button className="btn btn-primary" disabled={!usage} onClick={() => void confirmDeactivate()}>
              {t("geo.confirm")}
            </button>
            <button className="btn btn-secondary" onClick={() => setConfirmTarget(null)}>
              {t("geo.cancel")}
            </button>
          </div>
        </Section>
      )}

      {/* ── Ülke listesi ──────────────────────────────────────────── */}
      <Section title={t("geo.countries")}>
        <DataTable
          screenCode="COUNTRY_LIST"
          rowKey={(c) => c.id}
          rows={countries}
          loading={loading}
          fallbackColumns={FALLBACK_COLUMNS}
          emptyMessage={t("geo.empty")}
          renderers={{
            isoCode: (c) => (
              <span style={{ fontFamily: "monospace", ...(c.active ? {} : { opacity: 0.55 }) }}>{c.isoCode}</span>
            ),
            name: (c) =>
              editingId === c.id ? (
                <span style={{ display: "inline-flex", gap: 6, ...(c.active ? {} : { opacity: 0.55 }) }}>
                  <input
                    className="form-input"
                    style={{ padding: "4px 8px" }}
                    value={editingName}
                    onChange={(e) => setEditingName(e.target.value)}
                  />
                  <button className="btn btn-primary" style={smallBtn} onClick={() => void saveName(c.id)}>
                    {t("common.save")}
                  </button>
                  <button className="btn btn-secondary" style={smallBtn} onClick={() => setEditingId(null)}>
                    {t("geo.cancel")}
                  </button>
                </span>
              ) : (
                <span style={c.active ? undefined : { opacity: 0.55 }}>{c.name}</span>
              ),
            stateCount: (c) => <span style={c.active ? undefined : { opacity: 0.55 }}>{c.stateCount}</span>,
            cityCount: (c) => <span style={c.active ? undefined : { opacity: 0.55 }}>{c.cityCount}</span>,
            status: (c) => (
              <span style={{ color: c.active ? "var(--neon-green)" : "var(--neon-red)", ...(c.active ? {} : { opacity: 0.55 }) }}>
                {c.active ? t("geo.active") : t("geo.inactive")}
              </span>
            ),
            actions: (c) => (
              <span style={{ display: "inline-flex", gap: 6, flexWrap: "wrap", ...(c.active ? {} : { opacity: 0.55 }) }}>
                {c.active && (
                  <>
                    <button
                      className="btn btn-secondary"
                      style={smallBtn}
                      onClick={() => {
                        setSelected(c);
                        setSuccess(null);
                        setError(null);
                      }}
                    >
                      <Globe2 size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                      {t("geo.manage")}
                    </button>
                    <button
                      className="btn btn-secondary"
                      style={smallBtn}
                      onClick={() => openAddressTemplateConfig(c.id)}
                    >
                      {t("geo.openTemplate")}
                    </button>
                    <button
                      className="btn btn-secondary"
                      style={smallBtn}
                      onClick={() => {
                        setEditingId(c.id);
                        setEditingName(c.name);
                      }}
                    >
                      {t("addr.edit")}
                    </button>
                    <button className="btn btn-secondary" style={smallBtn} onClick={() => void askDeactivate(c)}>
                      <Power size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                      {t("geo.deactivate")}
                    </button>
                  </>
                )}
                {!c.active && (
                  <button className="btn btn-secondary" style={smallBtn} onClick={() => void reactivate(c.id)}>
                    <RotateCcw size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                    {t("geo.reactivate")}
                  </button>
                )}
              </span>
            ),
          }}
        />
      </Section>

      {/* ── Seçili ülke: idari birimler ───────────────────────────── */}
      {selected ? (
        <AdminUnitsPanel
          key={selected.id}
          country={selected}
          onChanged={loadCountries}
          onError={fail}
          onSuccess={notify}
        />
      ) : (
        <InfoBanner message={t("geo.selectCountryHint")} />
      )}
    </div>
  );
}

/**
 * Seçili ülkenin eyalet/şehir/ilçe/mahalle yönetim paneli.
 * Cascade veriler read-only /api/address endpoint'lerinden gelir;
 * yazma işlemleri /api/admin/geo'ya gider.
 */
function AdminUnitsPanel({
  country,
  onChanged,
  onError,
  onSuccess,
}: {
  country: CountryAdminDto;
  onChanged: () => void;
  onError: (e: unknown) => void;
  onSuccess: (msg: string) => void;
}) {
  const { t } = useI18n();

  const [states, setStates] = useState<StateProvinceDto[]>([]);
  const [cities, setCities] = useState<NamedDto[]>([]);
  const [districts, setDistricts] = useState<NamedDto[]>([]);
  const [neighborhoods, setNeighborhoods] = useState<NamedDto[]>([]);

  // Formlar
  const [stateName, setStateName] = useState("");
  const [stateCode, setStateCode] = useState("");
  const [csv, setCsv] = useState("");
  const [importResult, setImportResult] = useState<StateImportResultDto | null>(null);
  const [cityName, setCityName] = useState("");
  const [cityStateId, setCityStateId] = useState<number | "">("");
  const [assignStateId, setAssignStateId] = useState<number | "">("");
  const [selectedCityId, setSelectedCityId] = useState<number | "">("");
  const [districtName, setDistrictName] = useState("");
  const [selectedDistrictId, setSelectedDistrictId] = useState<number | "">("");
  const [neighborhoodName, setNeighborhoodName] = useState("");
  const [neighborhoodZip, setNeighborhoodZip] = useState("");

  const asList = <T,>(data: unknown, fallback: T[] = []): T[] =>
    Array.isArray(data) ? (data as T[]) : fallback;

  const loadStates = useCallback(() => {
    addressMasterService
      .states(country.id)
      .then((data) => setStates(asList(data)))
      .catch(onError);
  }, [country.id, onError]);

  const loadCities = useCallback(() => {
    addressMasterService
      .cities(country.id)
      .then((data) => setCities(asList(data)))
      .catch(onError);
  }, [country.id, onError]);

  useEffect(() => {
    loadStates();
    loadCities();
  }, [loadStates, loadCities]);

  useEffect(() => {
    setDistricts([]);
    setSelectedDistrictId("");
    if (selectedCityId !== "") {
      addressMasterService
        .districts(String(selectedCityId))
        .then((data) => setDistricts(asList(data)))
        .catch(onError);
    }
  }, [selectedCityId, onError]);

  useEffect(() => {
    setNeighborhoods([]);
    if (selectedDistrictId !== "") {
      addressMasterService
        .neighborhoods(String(selectedDistrictId))
        .then((data) => setNeighborhoods(asList(data)))
        .catch(onError);
    }
  }, [selectedDistrictId, onError]);

  const addState = async () => {
    try {
      await geoAdminService.addState(country.id, { name: stateName.trim(), code: stateCode.trim() || null });
      setStateName("");
      setStateCode("");
      onSuccess(t("geo.updated"));
      loadStates();
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const removeState = async (stateId: number) => {
    try {
      await geoAdminService.deactivateState(stateId);
      onSuccess(t("geo.deactivated"));
      loadStates();
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const runImport = async () => {
    try {
      const result = await geoAdminService.importStates(country.id, csv);
      setImportResult(result);
      setCsv("");
      loadStates();
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const addCity = async () => {
    try {
      await geoAdminService.addCity(country.id, {
        name: cityName.trim(),
        stateProvinceId: cityStateId === "" ? null : cityStateId,
      });
      setCityName("");
      onSuccess(t("geo.updated"));
      loadCities();
      onChanged();
    } catch (e) {
      onError(e);
    }
  };

  const assignCity = async () => {
    if (selectedCityId === "" || assignStateId === "") return;
    try {
      await geoAdminService.assignCityToState(selectedCityId, assignStateId);
      onSuccess(t("geo.updated"));
      loadCities();
    } catch (e) {
      onError(e);
    }
  };

  const addDistrict = async () => {
    if (selectedCityId === "") return;
    try {
      await geoAdminService.addDistrict(selectedCityId, { name: districtName.trim() });
      setDistrictName("");
      onSuccess(t("geo.updated"));
      addressMasterService.districts(String(selectedCityId)).then(setDistricts).catch(onError);
    } catch (e) {
      onError(e);
    }
  };

  const addNeighborhood = async () => {
    if (selectedDistrictId === "") return;
    try {
      await geoAdminService.addNeighborhood(selectedDistrictId, {
        name: neighborhoodName.trim(),
        zipCode: neighborhoodZip.trim() || null,
      });
      setNeighborhoodName("");
      setNeighborhoodZip("");
      onSuccess(t("geo.updated"));
      addressMasterService.neighborhoods(String(selectedDistrictId)).then(setNeighborhoods).catch(onError);
    } catch (e) {
      onError(e);
    }
  };

  return (
    <>
      {/* ── Eyaletler ─────────────────────────────────────────────── */}
      <Section title={`${t("geo.states")} — ${country.name}`}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 12 }}>
          <Field label={t("geo.name")}>
            <input className="form-input" value={stateName} onChange={(e) => setStateName(e.target.value)} />
          </Field>
          <Field label={t("geo.code")}>
            <input
              className="form-input"
              style={{ width: 100 }}
              value={stateCode}
              maxLength={10}
              onChange={(e) => setStateCode(e.target.value.toUpperCase())}
            />
          </Field>
          <button
            className="btn btn-primary"
            style={{ marginBottom: 14 }}
            disabled={!stateName.trim()}
            onClick={() => void addState()}
          >
            <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("geo.addState")}
          </button>
        </div>

        {states.length === 0 ? (
          <InfoBanner message={t("geo.empty")} />
        ) : (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
            {states.map((s) => (
              <span
                key={s.id}
                className="glass-card"
                style={{ padding: "6px 12px", display: "inline-flex", gap: 8, alignItems: "center", fontSize: "0.85rem" }}
              >
                {s.name}
                {s.code ? <span style={{ color: "var(--text-muted)", fontFamily: "monospace" }}>{s.code}</span> : null}
                <button
                  className="btn btn-secondary"
                  style={{ ...smallBtn, padding: "2px 8px" }}
                  title={t("geo.deactivate")}
                  onClick={() => void removeState(s.id)}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}

        <Field label={`${t("geo.csvImport")} — ${t("geo.csvHint")}`}>
          <textarea
            className="form-input"
            rows={4}
            value={csv}
            placeholder={"name,code\nİstanbul,34\nAnkara,06"}
            onChange={(e) => setCsv(e.target.value)}
          />
        </Field>
        <button className="btn btn-secondary" disabled={!csv.trim()} onClick={() => void runImport()}>
          <Upload size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
          {t("geo.import")}
        </button>
        {importResult && (
          <div style={{ marginTop: 10 }}>
            <InfoBanner
              message={
                <>
                  {t("geo.importResult")
                    .replace("{imported}", String(importResult.imported))
                    .replace("{skipped}", String(importResult.skipped))}
                  {importResult.errors.length > 0 && (
                    <ul style={{ margin: "6px 0 0 16px" }}>
                      {importResult.errors.map((err, i) => (
                        <li key={i}>{err}</li>
                      ))}
                    </ul>
                  )}
                </>
              }
            />
          </div>
        )}
      </Section>

      {/* ── Şehirler ──────────────────────────────────────────────── */}
      <Section title={`${t("geo.cities")} — ${country.name}`}>
        <div style={grid2}>
          <Field label={t("geo.name")}>
            <input className="form-input" value={cityName} onChange={(e) => setCityName(e.target.value)} />
          </Field>
          <Field label={t("addr.state")}>
            <select
              className="form-select"
              value={cityStateId === "" ? "" : String(cityStateId)}
              onChange={(e) => setCityStateId(e.target.value === "" ? "" : Number(e.target.value))}
            >
              <option value="">{t("geo.noState")}</option>
              {states.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <button className="btn btn-primary" disabled={!cityName.trim()} onClick={() => void addCity()}>
          <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
          {t("geo.addCity")}
        </button>

        <div style={{ ...grid2, marginTop: 18 }}>
          <Field label={t("geo.selectCity")}>
            <select
              className="form-select"
              value={selectedCityId === "" ? "" : String(selectedCityId)}
              onChange={(e) => setSelectedCityId(e.target.value === "" ? "" : Number(e.target.value))}
            >
              <option value="">—</option>
              {cities.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          {/* TR eyalet geçiş senaryosu: mevcut şehri eyalete bağla */}
          <Field label={t("geo.assignState")}>
            <span style={{ display: "flex", gap: 8 }}>
              <select
                className="form-select"
                value={assignStateId === "" ? "" : String(assignStateId)}
                onChange={(e) => setAssignStateId(e.target.value === "" ? "" : Number(e.target.value))}
              >
                <option value="">{t("geo.selectState")}</option>
                {states.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
              <button
                className="btn btn-secondary"
                disabled={selectedCityId === "" || assignStateId === ""}
                onClick={() => void assignCity()}
              >
                {t("geo.assignState")}
              </button>
            </span>
          </Field>
        </div>
      </Section>

      {/* ── İlçe / Mahalle ────────────────────────────────────────── */}
      {selectedCityId !== "" && (
        <Section title={t("geo.districts")}>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 12 }}>
            <Field label={t("geo.name")}>
              <input className="form-input" value={districtName} onChange={(e) => setDistrictName(e.target.value)} />
            </Field>
            <button
              className="btn btn-primary"
              style={{ marginBottom: 14 }}
              disabled={!districtName.trim()}
              onClick={() => void addDistrict()}
            >
              <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
              {t("geo.addDistrict")}
            </button>
          </div>

          <div style={grid2}>
            <Field label={t("geo.selectDistrict")}>
              <select
                className="form-select"
                value={selectedDistrictId === "" ? "" : String(selectedDistrictId)}
                onChange={(e) => setSelectedDistrictId(e.target.value === "" ? "" : Number(e.target.value))}
              >
                <option value="">—</option>
                {districts.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          {selectedDistrictId !== "" && (
            <>
              <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end", marginTop: 6 }}>
                <Field label={t("geo.addNeighborhood")}>
                  <input
                    className="form-input"
                    value={neighborhoodName}
                    onChange={(e) => setNeighborhoodName(e.target.value)}
                  />
                </Field>
                <Field label={t("geo.zipCode")}>
                  <input
                    className="form-input"
                    style={{ width: 120 }}
                    value={neighborhoodZip}
                    onChange={(e) => setNeighborhoodZip(e.target.value)}
                  />
                </Field>
                <button
                  className="btn btn-primary"
                  style={{ marginBottom: 14 }}
                  disabled={!neighborhoodName.trim()}
                  onClick={() => void addNeighborhood()}
                >
                  <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
                  {t("geo.addNeighborhood")}
                </button>
              </div>
              {neighborhoods.length > 0 && (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {neighborhoods.map((n) => (
                    <span key={n.id} className="glass-card" style={{ padding: "6px 12px", fontSize: "0.85rem" }}>
                      {n.name}
                    </span>
                  ))}
                </div>
              )}
            </>
          )}
        </Section>
      )}
    </>
  );
}
