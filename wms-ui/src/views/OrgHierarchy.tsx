/**
 * İş İsteri 1 — Organizasyon Yapısı ve Çoklu Lokasyon
 * Firma → Lokasyon ağacı + aktif lokasyonun canlı depo doluluk verisi.
 * WMS_ADMIN: depo oluşturma, düzenleme, deaktivasyon.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { Building2, MapPin, Pencil, Plus, Trash2, Warehouse } from "lucide-react";
import {
  addressMasterService,
  describeError,
  orgService,
  storageLocationService,
  type CountryDto,
  type CreateLocationRequest,
  type LocationDetailDto,
  type LocationSummaryDto,
  type LocationType,
  type RegionSummaryDto,
  type StorageLocationResponse,
  type UpdateLocationRequest,
} from "../api/services";
import { isWmsAdmin } from "../auth/roles";
import { DEMO_LOCATIONS, type TenantCompany, type TenantLocation } from "../hooks/useOrgData";
import { useI18n } from "../i18n/I18nContext";
import {
  ErrorBanner,
  Field,
  InfoBanner,
  Loading,
  PageHeader,
  Section,
  StatCard,
  SuccessBanner,
  grid2,
} from "../components/common";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const LOCATION_TYPES: LocationType[] = ["CENTRAL", "TRANSIT", "VIRTUAL", "DISTRIBUTION", "RETURN"];

const ORG_HIERARCHY_FALLBACK = [
  fallbackCol("type", "columns.org.type", 0),
  fallbackCol("name", "columns.org.name", 1),
  fallbackCol("parent", "columns.org.parent", 2),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true }),
];

interface OrgHierarchyRow {
  key: string;
  type: string;
  name: string;
  parent: string;
  companyId: number;
  location?: TenantLocation;
}

const TIMEZONE_PRESETS = [
  "Europe/Istanbul",
  "Europe/Berlin",
  "Europe/London",
  "America/New_York",
  "Asia/Dubai",
  "UTC",
];

function mapSummary(l: LocationSummaryDto): TenantLocation {
  return {
    id: l.id,
    companyId: l.companyId,
    name: l.name,
    timezone: l.timezone,
    active: l.active,
  };
}

interface Props {
  activeCompanyId: number | "";
  activeLocationId: number | "";
  companies: TenantCompany[];
  locations: TenantLocation[];
  setActiveCompanyId: (id: number) => void;
  setActiveLocationId: (id: number) => void;
  onCompanyChange: (companyId: number) => void;
  applyTenant: (companyId: number, locationId: number) => void;
  onLocationsChanged: (companyId: number) => Promise<void>;
}

const emptyForm = (companyId: number | "") => ({
  companyId: companyId === "" ? ("" as number | "") : companyId,
  name: "",
  type: "CENTRAL" as LocationType,
  timezone: "Europe/Istanbul",
  countryId: "" as number | "",
  regionId: "" as number | "",
  templateLocationId: "" as number | "",
});

export function OrgHierarchy({
  activeCompanyId,
  activeLocationId,
  companies,
  locations,
  setActiveCompanyId,
  setActiveLocationId,
  onCompanyChange,
  applyTenant,
  onLocationsChanged,
}: Props) {
  const { t, formatNumber } = useI18n();
  const admin = isWmsAdmin();
  const [critical, setCritical] = useState<StorageLocationResponse[] | null>(null);
  const [bins, setBins] = useState<StorageLocationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [treeLocations, setTreeLocations] = useState<TenantLocation[]>(locations);
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [pendingSwitch, setPendingSwitch] = useState<{ companyId: number; locationId: number } | null>(null);
  const [countries, setCountries] = useState<CountryDto[]>([]);
  const [regions, setRegions] = useState<RegionSummaryDto[]>([]);
  const [templateLocations, setTemplateLocations] = useState<TenantLocation[]>([]);
  const [form, setForm] = useState(emptyForm(activeCompanyId));

  const allLocations = treeLocations;

  const orgHierarchyRows = useMemo<OrgHierarchyRow[]>(() => {
    const rows: OrgHierarchyRow[] = [];
    for (const c of companies) {
      rows.push({ key: `c-${c.id}`, type: "COMPANY", name: c.name, parent: "—", companyId: c.id });
      for (const l of allLocations.filter((loc) => loc.companyId === c.id)) {
        rows.push({
          key: `l-${l.id}`,
          type: "LOCATION",
          name: l.name,
          parent: c.name,
          companyId: c.id,
          location: l,
        });
      }
    }
    return rows;
  }, [companies, allLocations]);

  const reloadTree = useCallback(async () => {
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
      setTreeLocations(DEMO_LOCATIONS);
      return;
    }
    if (companies.length === 0) return;
    try {
      const nested = await Promise.all(
        companies.map((c) => orgService.getLocations(c.id).then((locs) => locs.map(mapSummary))),
      );
      setTreeLocations(nested.flat());
    } catch {
      setTreeLocations(locations);
    }
  }, [companies, locations]);

  useEffect(() => {
    void reloadTree();
  }, [reloadTree]);

  useEffect(() => {
    if (!activeLocationId) return;
    setError(null);
    setCritical(null);
    setBins(null);
    Promise.allSettled([
      storageLocationService.utilization(80),
      storageLocationService.search({ isActive: true, size: 10 }),
    ]).then(([u, s]) => {
      if (u.status === "fulfilled") setCritical(u.value);
      if (s.status === "fulfilled") setBins(s.value.content);
      if (u.status === "rejected" && s.status === "rejected") setError(describeError(u.reason));
    });
  }, [activeLocationId]);

  useEffect(() => {
    if (!admin) return;
    addressMasterService.countries().then(setCountries).catch(() => setCountries([]));
  }, [admin]);

  useEffect(() => {
    if (!admin || !form.countryId) {
      setRegions([]);
      return;
    }
    orgService.getRegions(form.countryId).then(setRegions).catch(() => setRegions([]));
  }, [admin, form.countryId]);

  useEffect(() => {
    if (!admin || form.companyId === "") {
      setTemplateLocations([]);
      return;
    }
    orgService
      .getLocations(form.companyId)
      .then((locs) => setTemplateLocations(locs.map(mapSummary)))
      .catch(() => setTemplateLocations([]));
  }, [admin, form.companyId]);

  const switchTo = (companyId: number, locationId: number) => {
    if (companyId !== activeCompanyId) {
      void onCompanyChange(companyId);
    }
    setActiveCompanyId(companyId);
    setActiveLocationId(locationId);
    applyTenant(companyId, locationId);
  };

  const resetForm = () => {
    setEditingId(null);
    setForm(emptyForm(activeCompanyId));
  };

  const startEdit = async (companyId: number, locationId: number) => {
    setError(null);
    setSuccess(null);
    try {
      const detail: LocationDetailDto = await orgService.getLocation(companyId, locationId);
      let countryId: number | "" = "";
      if (countries.length > 0) {
        const countryList = countries.length > 0 ? await addressMasterService.countries() : [];
        for (const c of countryList) {
          const regs = await orgService.getRegions(c.id);
          if (regs.some((r) => r.id === detail.regionId)) {
            countryId = c.id;
            break;
          }
        }
      }
      setEditingId(locationId);
      setForm({
        companyId,
        name: detail.name,
        type: detail.type,
        timezone: detail.timezone,
        countryId,
        regionId: detail.regionId,
        templateLocationId: "",
      });
    } catch (e) {
      setError(describeError(e));
    }
  };

  const handleSubmit = async () => {
    if (form.companyId === "" || !form.name.trim() || form.regionId === "") {
      setError(t("org.location.name") + " / " + t("org.location.region"));
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      if (editingId != null) {
        const body: UpdateLocationRequest = {
          name: form.name.trim(),
          type: form.type,
          timezone: form.timezone,
          regionId: Number(form.regionId),
        };
        await orgService.updateLocation(form.companyId, editingId, body);
        setSuccess(t("org.location.updated"));
        resetForm();
      } else {
        const body: CreateLocationRequest = {
          name: form.name.trim(),
          type: form.type,
          timezone: form.timezone,
          regionId: Number(form.regionId),
          templateLocationId: form.templateLocationId === "" ? null : Number(form.templateLocationId),
        };
        const created = await orgService.createLocation(form.companyId, body);
        setSuccess(t("org.location.created"));
        setPendingSwitch({ companyId: form.companyId, locationId: created.id });
        resetForm();
      }
      await onLocationsChanged(form.companyId);
      await reloadTree();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleDeactivate = async (companyId: number, locationId: number) => {
    if (!confirm(t("org.location.deactivateConfirm"))) return;
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      await orgService.deactivateLocation(companyId, locationId);
      setSuccess(t("org.location.deactivated"));
      if (locationId === activeLocationId) {
        const locs = await orgService.getLocations(companyId);
        const next = locs[0]?.id;
        if (next) applyTenant(companyId, next);
      }
      await onLocationsChanged(companyId);
      await reloadTree();
      if (editingId === locationId) resetForm();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <PageHeader title={t("org.title")} subtitle={t("org.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      {pendingSwitch != null && (
        <InfoBanner
          message={
            <span>
              {t("org.location.created")}{" "}
              <button
                className="btn btn-secondary"
                style={{ marginLeft: 8, padding: "2px 10px", fontSize: "0.8rem" }}
                onClick={() => {
                  switchTo(pendingSwitch.companyId, pendingSwitch.locationId);
                  setPendingSwitch(null);
                }}
              >
                {t("org.location.switchToNew")}
              </button>
            </span>
          }
        />
      )}

      <div style={{ display: "flex", gap: 16, marginBottom: 20, flexWrap: "wrap" }}>
        <StatCard label={t("org.companies")} value={companies.length} />
        <StatCard label={t("org.locations")} value={allLocations.length} color="var(--neon-purple)" />
        <StatCard
          label={t("org.activeTenant")}
          value={
            <span style={{ fontSize: "0.95rem" }}>
              {companies.find((c) => c.id === activeCompanyId)?.name} /{" "}
              {allLocations.find((l) => l.id === activeLocationId)?.name}
            </span>
          }
          color="var(--neon-green)"
        />
      </div>

      {admin && (
        <Section title={t("org.location.manage")} style={{ marginBottom: 20 }}>
          <div style={grid2}>
            <Field label={t("org.location.company")}>
              <select
                className="form-input"
                value={form.companyId}
                disabled={editingId != null}
                onChange={(e) => {
                  const v = e.target.value === "" ? "" : Number(e.target.value);
                  setForm({ ...emptyForm(v), companyId: v });
                }}
              >
                <option value="">—</option>
                {companies.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </Field>
            <Field label={t("org.location.name")}>
              <input
                className="form-input"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </Field>
            <Field label={t("org.location.type")}>
              <select
                className="form-input"
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value as LocationType })}
              >
                {LOCATION_TYPES.map((lt) => (
                  <option key={lt} value={lt}>{t(`org.location.type.${lt}`)}</option>
                ))}
              </select>
            </Field>
            <Field label={t("org.location.timezone")}>
              <select
                className="form-input"
                value={form.timezone}
                onChange={(e) => setForm({ ...form, timezone: e.target.value })}
              >
                {TIMEZONE_PRESETS.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </select>
            </Field>
            <Field label={t("org.location.country")}>
              <select
                className="form-input"
                value={form.countryId}
                onChange={(e) => {
                  const v = e.target.value === "" ? "" : Number(e.target.value);
                  setForm({ ...form, countryId: v, regionId: "" });
                }}
              >
                <option value="">—</option>
                {countries.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </Field>
            <Field label={t("org.location.region")}>
              <select
                className="form-input"
                value={form.regionId}
                disabled={!form.countryId}
                onChange={(e) => setForm({ ...form, regionId: e.target.value === "" ? "" : Number(e.target.value) })}
              >
                <option value="">—</option>
                {regions.map((r) => (
                  <option key={r.id} value={r.id}>{r.name}</option>
                ))}
              </select>
            </Field>
            {!editingId && (
              <Field label={t("org.location.template")}>
                <select
                  className="form-input"
                  value={form.templateLocationId}
                  onChange={(e) =>
                    setForm({ ...form, templateLocationId: e.target.value === "" ? "" : Number(e.target.value) })
                  }
                >
                  <option value="">—</option>
                  {templateLocations.map((l) => (
                    <option key={l.id} value={l.id}>{l.name}</option>
                  ))}
                </select>
              </Field>
            )}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button className="btn btn-primary" disabled={busy} onClick={() => void handleSubmit()}>
              <Plus size={14} style={{ marginRight: 4, verticalAlign: "-2px" }} />
              {editingId != null ? t("common.update") : t("org.location.create")}
            </button>
            {editingId != null && (
              <button className="btn btn-secondary" disabled={busy} onClick={resetForm}>
                {t("common.cancel")}
              </button>
            )}
          </div>
        </Section>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "minmax(320px, 1fr) minmax(380px, 1.4fr)", gap: 20 }}>
        <Section title={t("org.title")} style={{ marginBottom: 0 }}>
          <DataTable
            screenCode="ORG_HIERARCHY_LIST"
            rowKey={(row) => row.key}
            rows={orgHierarchyRows}
            fallbackColumns={ORG_HIERARCHY_FALLBACK}
            renderers={{
              type: (row) => (
                <span className={`badge ${row.type === "COMPANY" ? "badge-blue" : "badge-purple"}`} style={{ fontSize: "0.68rem" }}>
                  {row.type === "COMPANY" ? t("org.companies") : t("org.locations")}
                </span>
              ),
              name: (row) => {
                const l = row.location;
                const isActiveTenant = l?.id === activeLocationId;
                const isInactive = l?.active === false;
                return (
                  <span style={{ opacity: isInactive ? 0.65 : 1 }}>
                    {row.type === "COMPANY" ? (
                      <Building2 size={14} color="var(--neon-blue)" style={{ marginRight: 6, verticalAlign: "-2px" }} />
                    ) : (
                      <MapPin size={14} color={isActiveTenant ? "var(--neon-green)" : "var(--text-secondary)"} style={{ marginRight: 6, verticalAlign: "-2px" }} />
                    )}
                    {row.name}
                    {l?.timezone && (
                      <span style={{ color: "var(--text-muted)", fontSize: "0.72rem", marginLeft: 6 }}>
                        ({l.timezone})
                      </span>
                    )}
                    {isInactive && (
                      <span className="badge badge-red" style={{ fontSize: "0.65rem", marginLeft: 6 }}>{t("common.passive")}</span>
                    )}
                    {isActiveTenant && (
                      <span className="badge badge-green" style={{ fontSize: "0.65rem", marginLeft: 6 }}>{t("common.active")}</span>
                    )}
                  </span>
                );
              },
              parent: (row) => row.parent,
              actions: (row) => {
                const l = row.location;
                if (!l) return "—";
                const isActiveTenant = l.id === activeLocationId;
                const isInactive = l.active === false;
                return (
                  <div style={{ display: "flex", gap: 4, alignItems: "center", flexWrap: "wrap" }}>
                    {admin && l.active !== false && (
                      <>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: "3px 8px", fontSize: "0.7rem" }}
                          title={t("org.location.edit")}
                          onClick={() => void startEdit(row.companyId, l.id)}
                        >
                          <Pencil size={12} />
                        </button>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: "3px 8px", fontSize: "0.7rem" }}
                          title={t("org.location.deactivate")}
                          disabled={busy}
                          onClick={() => void handleDeactivate(row.companyId, l.id)}
                        >
                          <Trash2 size={12} />
                        </button>
                      </>
                    )}
                    {!isActiveTenant && !isInactive && (
                      <button
                        className="btn btn-secondary"
                        style={{ padding: "3px 10px", fontSize: "0.75rem" }}
                        onClick={() => switchTo(row.companyId, l.id)}
                      >
                        {t("org.switchTo")}
                      </button>
                    )}
                  </div>
                );
              },
            }}
          />
        </Section>

        <Section title={t("org.utilization")} style={{ marginBottom: 0 }}>
          {critical === null && bins === null ? (
            <Loading label={t("common.loading")} />
          ) : (
            <>
              {critical && critical.length > 0 && (
                <>
                  <div style={{ color: "var(--neon-orange)", fontSize: "0.8rem", marginBottom: 8 }}>
                    {t("org.util.highOccupancy").replace("{count}", String(critical.length))}
                  </div>
                  <UtilTable rows={critical} formatNumber={formatNumber} t={t} />
                </>
              )}
              {bins && bins.length > 0 && (
                <>
                  <div style={{ color: "var(--text-muted)", fontSize: "0.8rem", margin: "14px 0 8px" }}>
                    <Warehouse size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                    {t("org.util.sampleBins")}
                  </div>
                  <UtilTable rows={bins} formatNumber={formatNumber} t={t} />
                </>
              )}
              {critical?.length === 0 && bins?.length === 0 && <InfoBanner message={t("common.empty")} />}
            </>
          )}
        </Section>
      </div>
    </div>
  );
}

function UtilTable({
  rows,
  formatNumber,
  t,
}: {
  rows: StorageLocationResponse[];
  formatNumber: (n?: number | null, d?: number) => string;
  t: (key: string) => string;
}) {
  return (
    <div className="table-container">
      <table className="wms-table">
        <thead>
          <tr>
            <th>{t("org.util.address")}</th>
            <th>{t("org.util.zone")}</th>
            <th>{t("org.util.occupancy")}</th>
            <th>{t("org.util.volume")}</th>
            <th>{t("org.util.status")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const pct = r.volumeUtilizationPercent ?? 0;
            const color = pct >= 90 ? "var(--neon-red)" : pct >= 80 ? "var(--neon-orange)" : "var(--neon-green)";
            return (
              <tr key={r.id}>
                <td style={{ fontFamily: "monospace" }}>{r.addressCode ?? `${r.aisle ?? ""}-${r.bay ?? ""}-${r.shelf ?? ""}-${r.bin ?? ""}`}</td>
                <td>{r.zoneCode ?? "—"}</td>
                <td style={{ color, fontWeight: 600 }}>{formatNumber(pct, 1)}</td>
                <td>{formatNumber(r.currentVolume, 1)} / {formatNumber(r.maxVolume, 1)}</td>
                <td>
                  <span className={`badge ${r.active ? "badge-green" : "badge-red"}`}>
                    {r.active ? t("common.active") : t("common.passive")}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
