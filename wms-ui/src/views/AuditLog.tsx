/**
 * İş İsteri 12 — Audit Log Görüntüleme
 */

import { useCallback, useEffect, useState } from "react";
import { auditService, describeError, type AuditLogEntry, type PageResp } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, InfoBanner, PageHeader, Section } from "../components/common";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const FALLBACK_COLUMNS = [
  fallbackCol("timestamp", "columns.audit.timestamp", 0, { dataType: "DATE" }),
  fallbackCol("user", "columns.audit.user", 1),
  fallbackCol("entity", "columns.audit.entity", 2),
  fallbackCol("action", "columns.audit.action", 3),
  fallbackCol("details", "columns.audit.details", 4),
];

export function AuditLog({ activeLocationId }: { activeLocationId?: number | "" }) {
  const { t, formatDateTime } = useI18n();
  const [page, setPage] = useState<PageResp<AuditLogEntry> | null>(null);
  const [pageNo, setPageNo] = useState(0);
  const [entityName, setEntityName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(() => {
    if (!activeLocationId) {
      setPage(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    auditService
      .logs({ page: pageNo, entityName: entityName || undefined })
      .then((p) => setPage(p))
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, [pageNo, entityName, activeLocationId]);

  useEffect(load, [load]);

  return (
    <div>
      <PageHeader title={t("audit.title")} subtitle={t("audit.subtitle")} />
      <ErrorBanner message={error} />

      <Section title={t("audit.title")}>
        {!activeLocationId ? (
          <InfoBanner message={t("common.tenantRequired")} />
        ) : (
        <>
        <div style={{ display: "flex", gap: 12, marginBottom: 16, flexWrap: "wrap" }}>
          <input
            className="form-input"
            style={{ maxWidth: 280 }}
            placeholder="entityName (örn. LocationProcessStepConfig)"
            value={entityName}
            onChange={(e) => { setPageNo(0); setEntityName(e.target.value); }}
          />
          <button className="btn btn-secondary" onClick={load}>{t("common.refresh")}</button>
        </div>

        <DataTable
          screenCode="AUDIT_LOG_LIST"
          rowKey={(row) => row.id}
          rows={page?.content ?? []}
          loading={loading}
          fallbackColumns={FALLBACK_COLUMNS}
          emptyMessage={t("common.empty")}
          renderers={{
            timestamp: (row) => formatDateTime(row.changedAt),
            user: (row) => row.changedByUserId ?? "—",
            entity: (row) => `${row.entityName} / ${row.entityId}`,
            action: (row) => row.actionType,
            details: (row) => {
              const parts: string[] = [];
              if (row.fieldName) parts.push(row.fieldName);
              if (row.oldValue != null) parts.push(`← ${row.oldValue}`);
              if (row.newValue != null) parts.push(`→ ${row.newValue}`);
              const text = parts.length > 0 ? parts.join(" ") : "—";
              return (
                <span style={{ maxWidth: 220, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "block" }}>
                  {text}
                </span>
              );
            },
          }}
        />
        {page && !loading && page.content.length > 0 && (
          <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 14 }}>
            <button className="btn btn-secondary" disabled={pageNo === 0} onClick={() => setPageNo((p) => p - 1)}>‹</button>
            <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem" }}>
              {pageNo + 1} / {Math.max(page.totalPages, 1)} — {page.totalElements} kayıt
            </span>
            <button className="btn btn-secondary" disabled={pageNo + 1 >= page.totalPages} onClick={() => setPageNo((p) => p + 1)}>›</button>
          </div>
        )}
        </>
        )}
      </Section>
    </div>
  );
}
