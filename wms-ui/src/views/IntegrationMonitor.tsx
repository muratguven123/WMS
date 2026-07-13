/**
 * İş İsteri 4 — ERP Entegrasyon İzleme
 * GET /api/integrations/logs (sayfalı + filtreli), log detayı (payload'lar),
 * POST /logs/{id}/retry ile manuel yeniden deneme, GET /stats ile 24s istatistik.
 */

import { useCallback, useEffect, useState } from "react";
import { Eye, RotateCcw, X } from "lucide-react";
import {
  describeError,
  integrationService,
  type IntegrationLogResponse,
  type PageResp,
} from "../api/services";
import { tenantContext } from "../api/wms-api-client";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { ErrorBanner, InfoBanner, Loading, PageHeader, Section, StatCard, SuccessBanner } from "../components/common";

/** Backend IntegrationStatus: SUCCESS | FAILED | RETRYING (kuyrukta bekleyenler RETRYING) */
const STATUSES = ["", "RETRYING", "SUCCESS", "FAILED"] as const;

const INTEGRATION_LOG_LIST_COLUMNS = [
  fallbackCol("id", "columns.common.id", 0, { dataType: "NUMBER" }),
  fallbackCol("type", "columns.integration.type", 1),
  fallbackCol("status", "columns.common.status", 2),
  fallbackCol("createdAt", "columns.common.createdAt", 3, { dataType: "DATE" }),
];

const statusBadge = (s: string) =>
  s === "SUCCESS" ? "badge-green" : s === "FAILED" ? "badge-red" : s === "RETRYING" ? "badge-orange" : "badge-blue";

interface IntegrationMonitorProps {
  activeLocationId?: number | "";
}

export function IntegrationMonitor({ activeLocationId }: IntegrationMonitorProps) {
  const { t, formatDateTime } = useI18n();
  const [page, setPage] = useState<PageResp<IntegrationLogResponse> | null>(null);
  const [pageNo, setPageNo] = useState(0);
  const [status, setStatus] = useState("");
  const [jobCode, setJobCode] = useState("");
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [detail, setDetail] = useState<IntegrationLogResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(() => {
    if (!activeLocationId) {
      setPage(null);
      setStats(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    Promise.allSettled([
      integrationService.logs({ page: pageNo, status: status || undefined, jobCode: jobCode || undefined }),
      integrationService.stats(),
    ]).then(([logs, st]) => {
      setLoading(false);
      if (logs.status === "fulfilled") setPage(logs.value);
      else setError(describeError(logs.reason));
      if (st.status === "fulfilled") setStats(st.value.counts);
    });
  }, [pageNo, status, jobCode, activeLocationId]);

  useEffect(load, [load]);

  const companyId = tenantContext.getCompanyId();
  useTopicSubscription(
    companyId ? stompDestinations.integrations(companyId) : null,
    (event) => {
      if (event.eventType !== "INTEGRATION_LOG_UPDATED") return;
      const p = event.payload;
      const logId = Number(p.logId ?? event.eventId ?? 0);
      if (!logId) return;
      const row: IntegrationLogResponse = {
        id: logId,
        status: String(p.status ?? "RETRYING"),
        jobCode: String(p.jobCode ?? "—"),
        erpSystemCode: String(p.system ?? "ERP"),
        errorMessage: p.errorMessage ? String(p.errorMessage) : undefined,
        createdAt: event.occurredAt,
        lastAttemptAt: event.occurredAt,
      };
      setPage((prev) => {
        if (!prev) return prev;
        const exists = prev.content.some((l) => l.id === logId);
        const content = exists ? prev.content : [row, ...prev.content].slice(0, prev.size);
        return { ...prev, content, totalElements: prev.totalElements + (exists ? 0 : 1) };
      });
      setStats((prev) => {
        if (!prev) return prev;
        const status = row.status;
        return { ...prev, [status]: (prev[status] ?? 0) + 1 };
      });
    },
  );

  const handleRetry = async (log: IntegrationLogResponse) => {
    setError(null);
    setSuccess(null);
    try {
      const resp = await integrationService.retry(log.id);
      setSuccess(`${t("int.retryScheduled")} — ${resp.message ?? resp.logId}`);
      load();
    } catch (e) {
      setError(describeError(e));
    }
  };

  const openDetail = async (log: IntegrationLogResponse) => {
    try {
      setDetail(await integrationService.logDetail(log.id));
    } catch {
      setDetail(log); // detay endpoint'i hata verirse listedeki veriyle göster
    }
  };

  return (
    <div>
      <PageHeader title={t("int.title")} subtitle={t("int.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      {stats && (
        <div style={{ display: "flex", gap: 16, marginBottom: 20, flexWrap: "wrap" }}>
          {Object.entries(stats).map(([k, v]) => (
            <StatCard
              key={k}
              label={`${t("int.last24h")} — ${k}`}
              value={v}
              color={k === "SUCCESS" ? "var(--neon-green)" : k === "FAILED" ? "var(--neon-red)" : "var(--neon-orange)"}
            />
          ))}
        </div>
      )}

      <Section title={t("int.title")}>
        {!activeLocationId ? (
          <InfoBanner message={t("common.tenantRequired")} />
        ) : (
        <>
        <div style={{ display: "flex", gap: 12, marginBottom: 16, flexWrap: "wrap" }}>
          <select className="form-select" style={{ maxWidth: 200 }} value={status} onChange={(e) => { setPageNo(0); setStatus(e.target.value); }}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{s || t("int.filterStatus")}</option>
            ))}
          </select>
          <input
            className="form-input"
            style={{ maxWidth: 240 }}
            placeholder={t("int.job")}
            value={jobCode}
            onChange={(e) => { setPageNo(0); setJobCode(e.target.value); }}
          />
          <button className="btn btn-secondary" onClick={load}>{t("common.refresh")}</button>
        </div>

        {loading && !page ? (
          <Loading label={t("common.loading")} />
        ) : !page || page.content.length === 0 ? (
          <InfoBanner message={t("common.empty")} />
        ) : (
          <>
            <DataTable
              screenCode="INTEGRATION_LOG_LIST"
              rowKey={(l) => l.id}
              rows={page.content}
              fallbackColumns={INTEGRATION_LOG_LIST_COLUMNS}
              renderers={{
                id: (l) => (
                  <div style={{ whiteSpace: "nowrap" }}>
                    <span style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{l.id}</span>
                    <div style={{ marginTop: 6 }}>
                      <button className="btn btn-secondary" style={{ padding: "4px 10px", marginRight: 6 }} onClick={() => void openDetail(l)}>
                        <Eye size={13} style={{ verticalAlign: "-2px" }} /> {t("common.detail")}
                      </button>
                      {(l.status === "FAILED" || l.status === "RETRYING") && (
                        <button className="btn btn-danger" style={{ padding: "4px 10px" }} onClick={() => void handleRetry(l)}>
                          <RotateCcw size={13} style={{ verticalAlign: "-2px" }} /> {t("int.retry")}
                        </button>
                      )}
                    </div>
                  </div>
                ),
                type: (l) => l.jobName ?? l.jobCode ?? l.erpSystemCode ?? "—",
                status: (l) => <span className={`badge ${statusBadge(l.status)}`}>{l.status}</span>,
                createdAt: (l) => (
                  <div>
                    <div style={{ whiteSpace: "nowrap" }}>{formatDateTime(l.createdAt)}</div>
                    {l.externalReference && (
                      <div style={{ fontFamily: "monospace", fontSize: "0.78rem", color: "var(--text-muted)", marginTop: 4 }}>
                        {l.externalReference}
                      </div>
                    )}
                    {l.errorMessage && (
                      <div style={{ maxWidth: 260, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: "var(--neon-red)", marginTop: 4 }}>
                        {l.errorMessage}
                      </div>
                    )}
                  </div>
                ),
              }}
            />
            <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 14 }}>
              <button className="btn btn-secondary" disabled={pageNo === 0} onClick={() => setPageNo((p) => p - 1)}>‹</button>
              <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem" }}>
                {pageNo + 1} / {Math.max(page.totalPages, 1)} — {page.totalElements} kayıt
              </span>
              <button className="btn btn-secondary" disabled={pageNo + 1 >= page.totalPages} onClick={() => setPageNo((p) => p + 1)}>›</button>
            </div>
          </>
        )}
        </>
        )}
      </Section>

      {detail && (
        <div className="modal-backdrop" onClick={() => setDetail(null)}>
          <div className="glass-card modal-wrapper" style={{ maxWidth: 860, width: "92%", maxHeight: "84vh", overflowY: "auto", padding: 24 }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
              <h3 style={{ color: "var(--text-primary)" }}>
                {detail.jobName ?? detail.jobCode} <span className={`badge ${statusBadge(detail.status)}`} style={{ marginLeft: 8 }}>{detail.status}</span>
              </h3>
              <button className="btn btn-secondary" style={{ padding: "4px 8px" }} onClick={() => setDetail(null)}><X size={15} /></button>
            </div>
            <div style={{ fontSize: "0.82rem", color: "var(--text-secondary)", marginBottom: 12 }}>
              ERP: {detail.erpSystemCode ?? "—"} · Ref: {detail.externalReference ?? "—"} · Son deneme: {formatDateTime(detail.lastAttemptAt)}
            </div>
            {detail.errorMessage && <ErrorBanner message={detail.errorMessage} />}
            {(["requestPayload", "responsePayload"] as const).map((k) => (
              <div key={k} style={{ marginBottom: 14 }}>
                <div style={{ fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: 1, color: "var(--text-muted)", marginBottom: 6 }}>
                  {k === "requestPayload" ? t("int.requestPayload") : t("int.responsePayload")}
                </div>
                <pre style={{ background: "var(--bg-primary)", border: "1px solid var(--glass-border)", borderRadius: 8, padding: 12, fontSize: "0.75rem", color: "var(--text-secondary)", overflowX: "auto", maxHeight: 220 }}>
                  {detail[k] ? tryPretty(detail[k]!) : "—"}
                </pre>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function tryPretty(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
