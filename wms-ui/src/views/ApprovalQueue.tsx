/**
 * İş İsteri 3 — Onay Kuyruğu
 * GET /api/approvals/pending ile bekleyen onay talepleri.
 */

import { useCallback, useEffect, useState } from "react";
import { CheckCircle, XCircle, ClipboardCheck } from "lucide-react";
import { approvalService, describeError, type ApprovalRequestDto } from "../api/services";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { ErrorBanner, Loading, PageHeader, Section, SuccessBanner } from "../components/common";

const APPROVAL_QUEUE_LIST_COLUMNS = [
  fallbackCol("id", "columns.common.id", 0, { dataType: "NUMBER" }),
  fallbackCol("type", "columns.approval.type", 1),
  fallbackCol("status", "columns.common.status", 2),
  fallbackCol("requestedAt", "columns.approval.requestedAt", 3, { dataType: "DATE" }),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true, dataType: "CUSTOM" }),
];

interface ApprovalQueueProps {
  activeCompanyId: number | "";
  activeLocationId: number | "";
}

export function ApprovalQueue({ activeCompanyId, activeLocationId }: ApprovalQueueProps) {
  const { t, formatDateTime } = useI18n();
  const [items, setItems] = useState<ApprovalRequestDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    if (!activeLocationId) {
      setItems([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    approvalService
      .listPending()
      .then(setItems)
      .catch((e) => setError(describeError(e)))
      .finally(() => setLoading(false));
  }, [activeLocationId]);

  useEffect(() => {
    load();
  }, [load]);

  const companyId = activeCompanyId === "" ? null : activeCompanyId;
  useTopicSubscription(
    companyId ? stompDestinations.approvals(companyId) : null,
    (event) => {
      if (event.eventType === "APPROVAL_CREATED") {
        const p = event.payload;
        const id = Number(p.approvalRequestId ?? 0);
        if (!id) return;
        setItems((prev) => {
          if (prev.some((x) => x.id === id)) return prev;
          const row: ApprovalRequestDto = {
            id,
            stepConfigId: Number(p.stepConfigId ?? 0),
            referenceType: String(p.referenceType ?? ""),
            referenceId: Number(p.referenceId ?? 0),
            requestedByUserId: Number(p.requestedByUserId ?? 0),
            status: "PENDING_APPROVAL",
            createdAt: event.occurredAt,
          };
          window.dispatchEvent(new CustomEvent("wms:approval-count", { detail: prev.length + 1 }));
          return [row, ...prev];
        });
      }
      if (event.eventType === "APPROVAL_RESOLVED") {
        const id = Number(event.payload.approvalRequestId ?? 0);
        if (!id) return;
        setItems((prev) => {
          const next = prev.filter((x) => x.id !== id);
          window.dispatchEvent(new CustomEvent("wms:approval-count", { detail: next.length }));
          return next;
        });
      }
    },
  );

  const act = async (id: number, action: "approve" | "reject") => {
    setBusyId(id);
    setError(null);
    setSuccess(null);
    try {
      if (action === "approve") await approvalService.approve(id);
      else await approvalService.reject(id);
      setSuccess(action === "approve" ? t("approval.approved") : t("approval.rejected"));
      load();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <PageHeader title={t("approval.title")} subtitle={t("approval.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <Section title={t("approval.title")}>
        {!activeLocationId ? (
          <p style={{ color: "var(--text-muted)" }}>{t("common.tenantRequired")}</p>
        ) : loading ? (
          <Loading label={t("common.loading")} />
        ) : items.length === 0 ? (
          <p style={{ color: "var(--text-muted)" }}>{t("approval.empty")}</p>
        ) : (
          <DataTable
            screenCode="APPROVAL_QUEUE_LIST"
            rowKey={(item) => item.id}
            rows={items}
            fallbackColumns={APPROVAL_QUEUE_LIST_COLUMNS}
            renderers={{
              id: (item) => (
                <>
                  <ClipboardCheck size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
                  <div style={{ fontSize: "0.7rem", fontFamily: "monospace", color: "var(--text-muted)" }}>
                    {item.referenceId}
                  </div>
                </>
              ),
              type: (item) => <strong>{item.referenceType}</strong>,
              status: (item) => <span className="badge badge-orange">{item.status}</span>,
              requestedAt: (item) => (item.createdAt ? formatDateTime(item.createdAt) : "—"),
              actions: (item) => (
                <div style={{ display: "flex", gap: 6 }}>
                  <button
                    className="btn btn-success"
                    style={{ padding: "4px 10px", fontSize: "0.75rem" }}
                    disabled={busyId === item.id}
                    onClick={() => void act(item.id, "approve")}
                  >
                    <CheckCircle size={12} style={{ marginRight: 4, verticalAlign: "-2px" }} />
                    {t("approval.approve")}
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: "4px 10px", fontSize: "0.75rem" }}
                    disabled={busyId === item.id}
                    onClick={() => void act(item.id, "reject")}
                  >
                    <XCircle size={12} style={{ marginRight: 4, verticalAlign: "-2px" }} />
                    {t("approval.reject")}
                  </button>
                </div>
              ),
            }}
          />
        )}
      </Section>
    </div>
  );
}
