/**
 * Firma Yönetimi — Company Admin CRUD
 *
 * Soft delete + reaktivasyon, kullanım kontrolü (usage) ve
 * vergi no benzersizlik doğrulaması içerir.
 * Yalnızca WMS_ADMIN erişir (bkz. auth/roles.ts).
 */

import { useCallback, useEffect, useState } from "react";
import { Plus, Power, RotateCcw, Pencil } from "lucide-react";
import {
  companyAdminService,
  describeError,
  type CompanyAdminDto,
  type CompanyUsageDto,
  type OrganizationOptionDto,
} from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner } from "../components/common";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { emitCompaniesChanged } from "../realtime/orgEvents";
import { FEATURES, isFeatureEnabled, isWmsAdmin } from "../auth/roles";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";

const FALLBACK_COLUMNS = [
  fallbackCol("name", "columns.company.name", 0, { locked: true }),
  fallbackCol("organizationName", "columns.company.organization", 1),
  fallbackCol("taxNumber", "columns.company.taxNumber", 2),
  fallbackCol("taxOffice", "columns.company.taxOffice", 3),
  fallbackCol("locationCount", "columns.company.locationCount", 4, { dataType: "NUMBER" }),
  fallbackCol("status", "columns.common.status", 5),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true, dataType: "CUSTOM" }),
];

const smallBtn = { padding: "4px 10px", fontSize: "0.8rem" } as const;
const TAX_PATTERN = /^[A-Za-z0-9]{5,50}$/;

export function CompanyManagement() {
  const { t } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [companies, setCompanies] = useState<CompanyAdminDto[]>([]);
  const [organizations, setOrganizations] = useState<OrganizationOptionDto[]>([]);
  const [loading, setLoading] = useState(false);

  // Yeni firma formu
  const [orgId, setOrgId] = useState<number | "">("");
  const [newName, setNewName] = useState("");
  const [newTax, setNewTax] = useState("");
  const [newOffice, setNewOffice] = useState("");

  // Satır içi düzenleme
  const [editing, setEditing] = useState<CompanyAdminDto | null>(null);
  const [editName, setEditName] = useState("");
  const [editTax, setEditTax] = useState("");
  const [editOffice, setEditOffice] = useState("");

  // Pasifleştirme onayı
  const [confirmTarget, setConfirmTarget] = useState<CompanyAdminDto | null>(null);
  const [usage, setUsage] = useState<CompanyUsageDto | null>(null);

  const notify = useCallback((msg: string) => {
    setSuccess(msg);
    setError(null);
  }, []);
  const fail = useCallback((e: unknown) => {
    setError(describeError(e));
    setSuccess(null);
  }, []);

  const loadCompanies = useCallback(() => {
    setLoading(true);
    companyAdminService
      .listCompanies()
      .then((data) => {
        if (!Array.isArray(data)) {
          setCompanies([]);
          setError(t("company.listInvalid"));
          return;
        }
        setCompanies(data);
      })
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, [t]);

  useEffect(() => {
    loadCompanies();
    companyAdminService
      .listOrganizations()
      .then(setOrganizations)
      .catch((e) => setError(describeError(e)));
  }, [loadCompanies]);

  useTopicSubscription(
    isWmsAdmin() && isFeatureEnabled(FEATURES.REALTIME) ? stompDestinations.orgCompanies() : null,
    () => {
      loadCompanies();
    },
  );

  const notifyOrgRefresh = () => emitCompaniesChanged();

  const canCreate =
    orgId !== "" &&
    newName.trim().length > 0 &&
    TAX_PATTERN.test(newTax.trim());

  const createCompany = async () => {
    if (orgId === "" || !newName.trim() || !TAX_PATTERN.test(newTax.trim())) return;
    try {
      await companyAdminService.createCompany({
        organizationId: orgId,
        name: newName.trim(),
        taxNumber: newTax.trim(),
        taxOffice: newOffice.trim() || null,
      });
      setOrgId("");
      setNewName("");
      setNewTax("");
      setNewOffice("");
      notify(t("company.created"));
      notifyOrgRefresh();
      loadCompanies();
    } catch (e) {
      fail(e);
    }
  };

  const startEdit = (c: CompanyAdminDto) => {
    setEditing(c);
    setEditName(c.name);
    setEditTax(c.taxNumber);
    setEditOffice(c.taxOffice ?? "");
    setConfirmTarget(null);
  };

  const saveEdit = async () => {
    if (!editing) return;
    if (!editName.trim() || !TAX_PATTERN.test(editTax.trim())) {
      setError(t("company.validation"));
      return;
    }
    try {
      await companyAdminService.updateCompany(editing.id, {
        name: editName.trim(),
        taxNumber: editTax.trim(),
        taxOffice: editOffice.trim() || null,
      });
      setEditing(null);
      notify(t("company.updated"));
      notifyOrgRefresh();
      loadCompanies();
    } catch (e) {
      fail(e);
    }
  };

  const askDeactivate = async (company: CompanyAdminDto) => {
    setEditing(null);
    setConfirmTarget(company);
    setUsage(null);
    try {
      setUsage(await companyAdminService.getCompanyUsage(company.id));
    } catch {
      setUsage({
        companyId: company.id,
        activeLocationCount: 0,
        userAccessCount: 0,
        transactionLogCount: 0,
        canDeactivate: false,
      });
    }
  };

  const confirmDeactivate = async () => {
    if (!confirmTarget || !usage?.canDeactivate) return;
    try {
      await companyAdminService.deactivateCompany(confirmTarget.id);
      setConfirmTarget(null);
      notify(t("company.deactivated"));
      notifyOrgRefresh();
      loadCompanies();
    } catch (e) {
      fail(e);
    }
  };

  const reactivate = async (companyId: number) => {
    try {
      await companyAdminService.reactivateCompany(companyId);
      notify(t("company.reactivated"));
      notifyOrgRefresh();
      loadCompanies();
    } catch (e) {
      fail(e);
    }
  };

  const usageMessage = (u: CompanyUsageDto | null): string => {
    if (!u) return t("common.loading");
    const base = t("company.usageInfo")
      .replace("{locations}", String(u.activeLocationCount))
      .replace("{users}", String(u.userAccessCount))
      .replace("{tx}", String(u.transactionLogCount));
    if (!u.canDeactivate) {
      return `${base} ${t("company.usageBlocked")}`;
    }
    return `${base} ${t("company.usageOk")}`;
  };

  return (
    <div>
      <PageHeader title={t("company.title")} subtitle={t("company.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <Section title={t("company.newCompany")}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
          <Field label={t("company.organization")}>
            <select
              className="form-input"
              style={{ minWidth: 200 }}
              value={orgId}
              onChange={(e) => setOrgId(e.target.value ? Number(e.target.value) : "")}
            >
              <option value="">{t("company.selectOrg")}</option>
              {organizations.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t("company.name")}>
            <input
              className="form-input"
              style={{ minWidth: 180 }}
              value={newName}
              maxLength={200}
              onChange={(e) => setNewName(e.target.value)}
            />
          </Field>
          <Field label={t("company.taxNumber")}>
            <input
              className="form-input"
              style={{ width: 160 }}
              value={newTax}
              maxLength={50}
              onChange={(e) => setNewTax(e.target.value.toUpperCase())}
              placeholder={t("company.taxHint")}
            />
          </Field>
          <Field label={t("company.taxOffice")}>
            <input
              className="form-input"
              style={{ minWidth: 160 }}
              value={newOffice}
              maxLength={200}
              onChange={(e) => setNewOffice(e.target.value)}
            />
          </Field>
          <button
            className="btn btn-primary"
            style={{ marginBottom: 14 }}
            disabled={!canCreate}
            onClick={() => void createCompany()}
          >
            <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("company.create")}
          </button>
        </div>
      </Section>

      {confirmTarget && (
        <Section title={`${t("company.confirmTitle")} — ${confirmTarget.name}`}>
          <InfoBanner message={usageMessage(usage)} />
          <div style={{ display: "flex", gap: 10 }}>
            <button
              className="btn btn-primary"
              disabled={!usage?.canDeactivate}
              onClick={() => void confirmDeactivate()}
            >
              {t("company.confirm")}
            </button>
            <button className="btn btn-secondary" onClick={() => setConfirmTarget(null)}>
              {t("company.cancel")}
            </button>
          </div>
        </Section>
      )}

      {editing && (
        <Section title={`${t("company.editTitle")} — ${editing.name}`}>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
            <Field label={t("company.name")}>
              <input
                className="form-input"
                value={editName}
                maxLength={200}
                onChange={(e) => setEditName(e.target.value)}
              />
            </Field>
            <Field label={t("company.taxNumber")}>
              <input
                className="form-input"
                value={editTax}
                maxLength={50}
                onChange={(e) => setEditTax(e.target.value.toUpperCase())}
              />
            </Field>
            <Field label={t("company.taxOffice")}>
              <input
                className="form-input"
                value={editOffice}
                maxLength={200}
                onChange={(e) => setEditOffice(e.target.value)}
              />
            </Field>
            <button className="btn btn-primary" style={{ marginBottom: 14 }} onClick={() => void saveEdit()}>
              {t("common.save")}
            </button>
            <button className="btn btn-secondary" style={{ marginBottom: 14 }} onClick={() => setEditing(null)}>
              {t("company.cancel")}
            </button>
          </div>
        </Section>
      )}

      <Section title={t("company.companies")}>
        <DataTable
          screenCode="COMPANY_LIST"
          rowKey={(c) => c.id}
          rows={companies}
          loading={loading}
          fallbackColumns={FALLBACK_COLUMNS}
          emptyMessage={t("company.empty")}
          renderers={{
            name: (c) => <span style={c.active ? undefined : { opacity: 0.55 }}>{c.name}</span>,
            organizationName: (c) => (
              <span style={c.active ? undefined : { opacity: 0.55 }}>{c.organizationName}</span>
            ),
            taxNumber: (c) => (
              <span style={{ fontFamily: "monospace", ...(c.active ? {} : { opacity: 0.55 }) }}>
                {c.taxNumber}
              </span>
            ),
            taxOffice: (c) => (
              <span style={c.active ? undefined : { opacity: 0.55 }}>{c.taxOffice ?? "—"}</span>
            ),
            locationCount: (c) => (
              <span style={c.active ? undefined : { opacity: 0.55 }}>{c.locationCount}</span>
            ),
            status: (c) => (
              <span
                style={{
                  color: c.active ? "var(--neon-green)" : "var(--neon-red)",
                  ...(c.active ? {} : { opacity: 0.55 }),
                }}
              >
                {c.active ? t("company.active") : t("company.inactive")}
              </span>
            ),
            actions: (c) => (
              <span
                style={{
                  display: "inline-flex",
                  gap: 6,
                  flexWrap: "wrap",
                  ...(c.active ? {} : { opacity: 0.55 }),
                }}
              >
                {c.active && (
                  <>
                    <button className="btn btn-secondary" style={smallBtn} onClick={() => startEdit(c)}>
                      <Pencil size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                      {t("company.edit")}
                    </button>
                    <button
                      className="btn btn-secondary"
                      style={smallBtn}
                      onClick={() => void askDeactivate(c)}
                    >
                      <Power size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                      {t("company.deactivate")}
                    </button>
                  </>
                )}
                {!c.active && (
                  <button
                    className="btn btn-secondary"
                    style={smallBtn}
                    onClick={() => void reactivate(c.id)}
                  >
                    <RotateCcw size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                    {t("company.reactivate")}
                  </button>
                )}
              </span>
            ),
          }}
        />
      </Section>
    </div>
  );
}
