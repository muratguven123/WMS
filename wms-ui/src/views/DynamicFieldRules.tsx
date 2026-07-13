/**
 * İş İsteri 5 — Dinamik Ekran Alan Yönetimi
 * Sol: ekran şeması önizleme (GET /api/ui/screens/{code}/schema) — kuralların
 * çözümlenmiş sonucu DynamicForm ile canlı render edilir.
 * Sağ: kural CRUD (POST/PUT/DELETE /api/ui/rules).
 */

import { useState } from "react";
import { Layers, Trash2 } from "lucide-react";
import {
  describeError,
  dynamicUiService,
  type ResolvedScreenDto,
  type UpsertRuleRequest,
} from "../api/services";
import { DynamicForm } from "../components/DynamicForm";
import { DataTable } from "../components/DataTable";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";
import { fallbackCol } from "../components/tableUtils";

const BEHAVIORS = ["MANDATORY", "OPTIONAL", "HIDDEN", "READ_ONLY"];
const OPERATIONS = ["", "CREATE", "EDIT", "VIEW"];

const behaviorBadge = (b: string) =>
  b === "MANDATORY" ? "badge-red" : b === "HIDDEN" ? "badge-orange" : b === "READ_ONLY" ? "badge-purple" : "badge-green";

const UI_RULE_FALLBACK = [
  fallbackCol("id", "columns.common.id", 0, { dataType: "NUMBER" }),
  fallbackCol("field", "columns.ui.field", 1),
  fallbackCol("behavior", "columns.ui.behavior", 2),
  fallbackCol("priority", "columns.ui.priority", 3, { dataType: "NUMBER" }),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true }),
];

const emptyRule: UpsertRuleRequest = { screenFieldId: 0, behavior: "OPTIONAL" };

export function DynamicFieldRules() {
  const { t } = useI18n();
  const [screenCode, setScreenCode] = useState("REC_CONTROL_FORM");
  const [schema, setSchema] = useState<ResolvedScreenDto | null>(null);
  const [rule, setRule] = useState<UpsertRuleRequest>(emptyRule);
  const [deleteId, setDeleteId] = useState<number | "">("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const numericRuleKeys = new Set<keyof UpsertRuleRequest>([
    "screenFieldId",
    "priority",
    "companyId",
    "countryId",
    "locationId",
    "roleId",
  ]);

  const loadSchema = async () => {
    setError(null);
    setSuccess(null);
    try {
      setSchema(await dynamicUiService.getSchema(screenCode.trim()));
    } catch (e) {
      setSchema(null);
      setError(describeError(e));
    }
  };

  const set = (k: keyof UpsertRuleRequest) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const raw = e.target.value;
    const val = numericRuleKeys.has(k)
      ? (raw === "" ? undefined : Number(raw))
      : (raw || null);
    setRule((r) => ({ ...r, [k]: val }));
  };

  const createRule = async () => {
    setError(null);
    setSuccess(null);
    try {
      const created = await dynamicUiService.createRule({
        ...rule,
        priority: rule.priority ? Number(rule.priority) : null,
      });
      setSuccess(`${t("dui.ruleCreated")} — id: ${created.id}`);
      setRule(emptyRule);
      if (schema) void loadSchema(); // önizlemeyi tazele
    } catch (e) {
      setError(describeError(e));
    }
  };

  const removeRule = async () => {
    if (deleteId === "") return;
    setError(null);
    setSuccess(null);
    try {
      await dynamicUiService.deleteRule(deleteId);
      setSuccess(t("dui.ruleDeleted"));
      setDeleteId("");
      if (schema) void loadSchema();
    } catch (e) {
      setError(describeError(e));
    }
  };

  return (
    <div>
      <PageHeader title={t("dui.title")} subtitle={t("dui.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(400px, 1fr))", gap: 20 }}>
        <div>
          <Section title={t("dui.preview")} style={{ marginBottom: 20 }}>
            <div style={{ display: "flex", gap: 12, alignItems: "flex-end", marginBottom: 8 }}>
              <div style={{ flex: 1 }}>
                <Field label={t("dui.screenCode")}>
                  <input
                    className="form-input"
                    value={screenCode}
                    placeholder="REC_CONTROL_FORM"
                    onChange={(e) => setScreenCode(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && void loadSchema()}
                  />
                </Field>
              </div>
              <button className="btn btn-primary" style={{ marginBottom: 14 }} onClick={() => void loadSchema()}>
                {t("dui.loadSchema")}
              </button>
            </div>

            {schema ? (
              <>
                <div style={{ marginBottom: 14, color: "var(--text-secondary)", fontSize: "0.85rem" }}>
                  <Layers size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} color="var(--neon-blue)" />
                  {schema.screenNameKey ? t(schema.screenNameKey) : schema.screenName} · {schema.fields.length} {t("dui.field")}
                </div>
                <DataTable
                  screenCode="UI_RULE_LIST"
                  rowKey={(f) => f.fieldKey}
                  rows={schema.fields}
                  showColumnPicker={false}
                  fallbackColumns={UI_RULE_FALLBACK}
                  renderers={{
                    field: (f) => (
                      <>
                        <span style={{ fontFamily: "monospace" }}>{f.fieldKey}</span>
                        <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)" }}>{f.dataType}</span>
                      </>
                    ),
                    behavior: (f) => <span className={`badge ${behaviorBadge(f.behavior)}`}>{f.behavior}</span>,
                    priority: () => "—",
                  }}
                />
                <h4 style={{ color: "var(--text-primary)", marginBottom: 12, fontSize: "0.9rem" }}>{t("dui.liveForm")}</h4>
                <DynamicForm schema={schema} onSubmit={() => setSuccess("Form validasyonu geçti (demo submit).")} submitLabel={t("common.save")} />
              </>
            ) : (
              <InfoBanner message={t("dui.schemaHint")} />
            )}
          </Section>
        </div>

        <div>
          <Section title={t("dui.newRule")} style={{ marginBottom: 20 }}>
            <div style={grid2}>
              <Field label={t("dui.screenFieldId")}>
                <input className="form-input" value={rule.screenFieldId || ""} onChange={set("screenFieldId")} placeholder="Screen field ID" />
              </Field>
              <Field label={t("dui.behavior")}>
                <select className="form-select" value={rule.behavior} onChange={set("behavior")}>
                  {BEHAVIORS.map((b) => <option key={b} value={b}>{b}</option>)}
                </select>
              </Field>
              <Field label={t("dui.priority")}>
                <input className="form-input" type="number" value={rule.priority ?? ""} onChange={set("priority")} placeholder="otomatik" />
              </Field>
              <Field label="operationType">
                <select className="form-select" value={rule.operationType ?? ""} onChange={set("operationType")}>
                  {OPERATIONS.map((o) => <option key={o} value={o}>{o || "—"}</option>)}
                </select>
              </Field>
              <Field label="companyId (ops.)">
                <input className="form-input" value={rule.companyId ?? ""} onChange={set("companyId")} />
              </Field>
              <Field label="countryId (ops.)">
                <input className="form-input" value={rule.countryId ?? ""} onChange={set("countryId")} />
              </Field>
              <Field label="locationId (ops.)">
                <input className="form-input" value={rule.locationId ?? ""} onChange={set("locationId")} />
              </Field>
              <Field label="roleId (ops.)">
                <input className="form-input" value={rule.roleId ?? ""} onChange={set("roleId")} />
              </Field>
              <Field label="defaultValue (ops.)">
                <input className="form-input" value={rule.defaultValue ?? ""} onChange={set("defaultValue")} />
              </Field>
              <Field label="validationRegex (ops.)">
                <input className="form-input" value={rule.validationRegex ?? ""} onChange={set("validationRegex")} placeholder="^\d{5}$" />
              </Field>
            </div>
            <InfoBanner message={t("dui.priorityHint")} />
            <button className="btn btn-primary" disabled={!rule.screenFieldId} onClick={() => void createRule()}>
              {t("common.add")}
            </button>
          </Section>

          <Section title={t("common.delete")} style={{ marginBottom: 0 }}>
            <div style={{ display: "flex", gap: 12, alignItems: "flex-end" }}>
              <div style={{ flex: 1 }}>
                <Field label={t("dui.ruleId")}>
                  <input
                    className="form-input"
                    value={deleteId === "" ? "" : String(deleteId)}
                    onChange={(e) => {
                      const v = e.target.value;
                      setDeleteId(v === "" ? "" : Number(v));
                    }}
                  />
                </Field>
              </div>
              <button className="btn btn-danger" style={{ marginBottom: 14 }} disabled={!deleteId} onClick={() => void removeRule()}>
                <Trash2 size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
                {t("common.delete")}
              </button>
            </div>
          </Section>
        </div>
      </div>
    </div>
  );
}
