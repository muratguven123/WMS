/**
 * Kullanıcı Yönetimi — yalnızca WMS_ADMIN
 */

import { useEffect, useState } from "react";
import { UserPlus, Users, Trash2 } from "lucide-react";
import {
  describeError,
  userService,
  orgService,
  type CreateUserRequest,
  type UserSummaryDto,
  type RoleSummaryDto,
} from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { useOrgData } from "../hooks/useOrgData";
import {
  ErrorBanner,
  Field,
  PageHeader,
  Section,
  SuccessBanner,
  grid2,
} from "../components/common";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const FALLBACK_COLUMNS = [
  fallbackCol("username", "columns.user.username", 0),
  fallbackCol("email", "columns.user.email", 1),
  fallbackCol("status", "columns.common.status", 2),
  fallbackCol("role", "columns.user.role", 3),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true, dataType: "CUSTOM" }),
];

export function UserManagement() {
  const { t } = useI18n();
  const { companies } = useOrgData();
  const [users, setUsers] = useState<UserSummaryDto[] | null>(null);
  const [roles, setRoles] = useState<RoleSummaryDto[]>([]);
  const [keycloakRoles, setKeycloakRoles] = useState<string[]>([]);
  const [locations, setLocations] = useState<{ id: number; name: string }[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    keycloakRoles: [] as string[],
    companyId: "" as number | "",
    locationId: "" as number | "",
    roleId: "" as number | "",
  });

  const load = () => {
    setError(null);
    userService
      .list()
      .then(setUsers)
      .catch((e) => {
        setError(describeError(e));
        setUsers([]);
      });
  };

  useEffect(() => {
    load();
    userService.listRoles().then(setRoles).catch(() => {});
    userService.listKeycloakRoles().then(setKeycloakRoles).catch(() => {});
  }, []);

  useEffect(() => {
    if (!form.companyId) {
      setLocations([]);
      return;
    }
    orgService
      .getLocations(form.companyId)
      .then((locs) => setLocations(locs.map((l) => ({ id: l.id, name: l.name }))))
      .catch(() => setLocations([]));
  }, [form.companyId]);

  const toggleRole = (role: string) => {
    setForm((f) => ({
      ...f,
      keycloakRoles: f.keycloakRoles.includes(role)
        ? f.keycloakRoles.filter((r) => r !== role)
        : [...f.keycloakRoles, role],
    }));
  };

  const handleCreate = async () => {
    if (!form.username || !form.email || !form.password || !form.companyId || !form.roleId) return;
    if (form.keycloakRoles.length === 0) {
      setError("En az bir sistem rolü seçin");
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const req: CreateUserRequest = {
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        keycloakRoles: form.keycloakRoles,
        accesses: [{
          companyId: form.companyId,
          locationId: form.locationId || null,
          roleId: form.roleId,
        }],
      };
      await userService.create(req);
      setSuccess(t("user.created"));
      setForm({
        username: "", email: "", password: "", firstName: "", lastName: "",
        keycloakRoles: [], companyId: "", locationId: "", roleId: "",
      });
      load();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleDeactivate = async (userId: number) => {
    if (!confirm("Bu kullanıcıyı devre dışı bırakmak istediğinize emin misiniz?")) return;
    setError(null);
    try {
      await userService.deactivate(userId);
      setSuccess(t("user.deactivated"));
      load();
    } catch (e) {
      setError(describeError(e));
    }
  };

  return (
    <div>
      <PageHeader title={t("user.title")} subtitle={t("user.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <Section title={t("user.title")}>
        <DataTable
          screenCode="USER_LIST"
          rowKey={(u) => u.id}
          rows={users ?? []}
          loading={users === null}
          fallbackColumns={FALLBACK_COLUMNS}
          emptyMessage={t("common.empty")}
          renderers={{
            username: (u) => (
              <>
                <Users size={14} style={{ marginRight: 6, verticalAlign: "-2px" }} color="var(--neon-blue)" />
                {u.username}
              </>
            ),
            email: (u) => u.email,
            status: (u) => (
              <span className={`badge ${u.active ? "badge-green" : "badge-red"}`}>
                {u.active ? t("common.active") : t("common.passive")}
              </span>
            ),
            role: (u) => u.accesses.map((a) => a.roleName).join(", ") || "—",
            actions: (u) =>
              u.active && u.username !== "demo.user" ? (
                <button
                  className="btn btn-secondary"
                  style={{ padding: "4px 10px" }}
                  onClick={() => void handleDeactivate(u.id)}
                >
                  <Trash2 size={13} /> {t("common.delete")}
                </button>
              ) : null,
          }}
        />
      </Section>

      <Section title={t("user.addNew")}>
        <div style={grid2}>
          <Field label={t("user.username")}>
            <input className="form-input" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </Field>
          <Field label={t("user.email")}>
            <input className="form-input" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </Field>
          <Field label={t("user.password")}>
            <input className="form-input" type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </Field>
          <Field label={t("user.firstName")}>
            <input className="form-input" value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
          </Field>
          <Field label={t("user.lastName")}>
            <input className="form-input" value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          </Field>
          <Field label={t("user.company")}>
            <select
              className="form-input"
              value={form.companyId === "" ? "" : String(form.companyId)}
              onChange={(e) => {
                const v = e.target.value;
                setForm({ ...form, companyId: v === "" ? "" : Number(v), locationId: "" });
              }}
            >
              <option value="">—</option>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </Field>
          <Field label={t("user.location")}>
            <select
              className="form-input"
              value={form.locationId === "" ? "" : String(form.locationId)}
              onChange={(e) => {
                const v = e.target.value;
                setForm({ ...form, locationId: v === "" ? "" : Number(v) });
              }}
            >
              <option value="">{t("user.allLocations")}</option>
              {locations.map((l) => (
                <option key={l.id} value={l.id}>{l.name}</option>
              ))}
            </select>
          </Field>
          <Field label={t("user.role")}>
            <select
              className="form-input"
              value={form.roleId === "" ? "" : String(form.roleId)}
              onChange={(e) => {
                const v = e.target.value;
                setForm({ ...form, roleId: v === "" ? "" : Number(v) });
              }}
            >
              <option value="">—</option>
              {roles.map((r) => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label={t("user.keycloakRoles")}>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 6 }}>
            {keycloakRoles.map((role) => (
              <label key={role} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 13, cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={form.keycloakRoles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                {role}
              </label>
            ))}
          </div>
        </Field>

        <button className="btn btn-primary" disabled={busy} onClick={() => void handleCreate()} style={{ marginTop: 16 }}>
          <UserPlus size={15} style={{ verticalAlign: "-2px", marginRight: 4 }} />
          {t("user.addNew")}
        </button>
      </Section>
    </div>
  );
}
