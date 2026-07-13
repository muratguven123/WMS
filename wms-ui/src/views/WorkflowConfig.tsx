/**
 * İş İsteri 3 — Lokasyon Bazlı Süreç Konfigürasyonu
 */

import { useCallback, useEffect, useState } from "react";
import { GitBranch, RefreshCcw } from "lucide-react";
import { describeError, processConfigService, type StepConfig } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner } from "../components/common";

const WORKFLOW_CONFIG_LIST_COLUMNS = [
  fallbackCol("code", "columns.workflow.code", 0),
  fallbackCol("name", "columns.workflow.name", 1),
  fallbackCol("status", "columns.common.status", 2),
];

interface Props {
  activeLocationId: number | "";
}

export function WorkflowConfig({ activeLocationId }: Props) {
  const { t } = useI18n();
  const [processCode, setProcessCode] = useState("INBOUND");
  const [steps, setSteps] = useState<StepConfig[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

  const loadSteps = useCallback(() => {
    if (!activeLocationId) return;
    setLoading(true);
    setError(null);
    processConfigService
      .listSteps(activeLocationId, processCode)
      .then(setSteps)
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, [activeLocationId, processCode]);

  useEffect(() => {
    loadSteps();
  }, [loadSteps]);

  const patchStep = async (step: StepConfig, patch: Partial<StepConfig>) => {
    setBusyId(step.id);
    setError(null);
    setSuccess(null);
    try {
      const updated = await processConfigService.updateStep(step.id, {
        sequence: (patch.sequence ?? step.sequence) as number | undefined,
        mandatory: (patch.mandatory ?? step.mandatory) as boolean | undefined,
        active: (patch.active ?? step.active) as boolean | undefined,
        requiresApproval: (patch.requiresApproval ?? step.requiresApproval) as boolean | undefined,
        responsibleRoleId: patch.responsibleRoleId ?? step.responsibleRoleId,
      });
      setSteps((prev) => prev.map((s) => (s.id === updated.id ? { ...s, ...updated } : s)));
      setSuccess(`${updated.stepCode ?? updated.id} güncellendi — değişiklik audit log'a yazıldı.`);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusyId(null);
    }
  };

  const Toggle = ({ step, field, label }: { step: StepConfig; field: "mandatory" | "active" | "requiresApproval"; label: string }) => {
    const val = Boolean(step[field]);
    return (
      <button
        className={`btn ${val ? "btn-success" : "btn-secondary"}`}
        style={{ padding: "4px 10px", fontSize: "0.72rem" }}
        disabled={busyId === step.id}
        onClick={() => void patchStep(step, { [field]: !val })}
        title={label}
      >
        {label}: {val ? t("common.yes") : t("common.no")}
      </button>
    );
  };

  return (
    <div>
      <PageHeader title={t("wf.title")} subtitle={t("wf.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />
      <InfoBanner message={t("wf.hint")} />

      <Section title={t("wf.load")}>
        <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" }}>
          <Field label={t("wf.processCode")}>
            <select className="form-select" value={processCode} onChange={(e) => setProcessCode(e.target.value)}>
              <option value="INBOUND">INBOUND</option>
              <option value="OUTBOUND">OUTBOUND</option>
            </select>
          </Field>
          <button className="btn btn-primary" style={{ marginBottom: 14 }} onClick={() => void loadSteps()} disabled={loading}>
            <RefreshCcw size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
            {t("common.refresh")}
          </button>
        </div>
      </Section>

      <Section title={t("wf.title")}>
        {loading ? (
          <p style={{ color: "var(--text-muted)" }}>{t("common.loading")}</p>
        ) : steps.length === 0 ? (
          <InfoBanner message={t("common.empty")} />
        ) : (
          <DataTable
            screenCode="WORKFLOW_CONFIG_LIST"
            rowKey={(s) => s.id}
            rows={steps}
            fallbackColumns={WORKFLOW_CONFIG_LIST_COLUMNS}
            renderers={{
              code: (s) => (
                <>
                  <GitBranch size={14} color="var(--neon-purple)" style={{ verticalAlign: "-2px", marginRight: 6 }} />
                  <strong>{String(s.stepCode ?? s.stepName ?? s.id)}</strong>
                </>
              ),
              name: (s) => (
                <input
                  className="form-input"
                  type="number"
                  style={{ width: 80, padding: "4px 8px" }}
                  defaultValue={s.sequence ?? 0}
                  onBlur={(e) => {
                    const v = parseInt(e.target.value, 10);
                    if (!isNaN(v) && v !== s.sequence) void patchStep(s, { sequence: v });
                  }}
                />
              ),
              status: (s) => (
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  <Toggle step={s} field="active" label={t("common.active")} />
                  <Toggle step={s} field="mandatory" label={t("wf.mandatory")} />
                  <Toggle step={s} field="requiresApproval" label={t("wf.requiresApproval")} />
                </div>
              ),
            }}
          />
        )}
      </Section>
    </div>
  );
}
