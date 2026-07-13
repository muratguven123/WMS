import { useCallback, useState } from "react";
import { Radio, Trash2, Zap } from "lucide-react";
import { useStomp, useTopicSubscription } from "../realtime/StompProvider";
import { stompDestinations } from "../realtime/destinations";
import type { DomainEventMessage } from "../realtime/types";
import { describeError, integrationService } from "../api/services";
import { DataTable } from "../components/DataTable";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, InfoBanner, PageHeader, Section, StatCard, SuccessBanner } from "../components/common";
import { fallbackCol } from "../components/tableUtils";

const REALTIME_TEST_FALLBACK = [
  fallbackCol("time", "columns.common.time", 0, { dataType: "DATE" }),
  fallbackCol("topic", "columns.realtime.topic", 1),
  fallbackCol("payload", "columns.realtime.payload", 2),
];

const MAX_LOG_ENTRIES = 100;

interface LogEntry {
  id: string;
  receivedAt: string;
  topicLabel: string;
  eventType: string;
  payloadPreview: string;
}

function isOfflineMode(): boolean {
  return Boolean((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode);
}

function formatPayload(payload: Record<string, unknown>): string {
  try {
    const text = JSON.stringify(payload);
    return text.length > 120 ? `${text.slice(0, 120)}…` : text;
  } catch {
    return "—";
  }
}

function toLogEntry(topicLabel: string, event: DomainEventMessage): LogEntry {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    receivedAt: new Date().toISOString(),
    topicLabel,
    eventType: event.eventType,
    payloadPreview: formatPayload(event.payload),
  };
}

export function RealtimeTest() {
  const { t, formatDateTime } = useI18n();
  const { connected, userId, companyId, locationId } = useStomp();
  const offline = isOfflineMode();

  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [triggering, setTriggering] = useState(false);
  const [triggerError, setTriggerError] = useState<string | null>(null);
  const [triggerSuccess, setTriggerSuccess] = useState<string | null>(null);

  const appendLog = useCallback((topicLabel: string, event: DomainEventMessage) => {
    const entry = toLogEntry(topicLabel, event);
    setLogs((prev) => [entry, ...prev].slice(0, MAX_LOG_ENTRIES));
    setTotalCount((n) => n + 1);
  }, []);

  const triggerTestEvent = async () => {
    if (companyId == null || locationId == null) return;
    setTriggering(true);
    setTriggerError(null);
    setTriggerSuccess(null);
    try {
      await integrationService.enqueueTestMovement(companyId, locationId);
      setTriggerSuccess(t("rt.triggerQueued"));
    } catch (e) {
      setTriggerError(describeError(e));
    } finally {
      setTriggering(false);
    }
  };

  const stockDest =
    companyId != null && locationId != null
      ? stompDestinations.stock(companyId, locationId)
      : null;
  const locationsDest =
    companyId != null && locationId != null
      ? stompDestinations.locations(companyId, locationId)
      : null;
  const approvalsDest = companyId != null ? stompDestinations.approvals(companyId) : null;
  const integrationsDest = companyId != null ? stompDestinations.integrations(companyId) : null;
  const tasksDest =
    companyId != null && locationId != null
      ? stompDestinations.tasks(companyId, locationId)
      : null;
  const operatorsDest =
    companyId != null && locationId != null
      ? stompDestinations.operators(companyId, locationId)
      : null;
  const userApprovalsDest = userId ? stompDestinations.userApprovals(userId) : null;
  const userTasksDest = userId ? stompDestinations.userTasks(userId) : null;

  useTopicSubscription(stockDest, (e) => appendLog("stock", e));
  useTopicSubscription(locationsDest, (e) => appendLog("locations", e));
  useTopicSubscription(approvalsDest, (e) => appendLog("approvals", e));
  useTopicSubscription(integrationsDest, (e) => appendLog("integrations", e));
  useTopicSubscription(tasksDest, (e) => appendLog("tasks", e));
  useTopicSubscription(operatorsDest, (e) => appendLog("operators", e));
  useTopicSubscription(userApprovalsDest, (e) => appendLog("userApprovals", e));
  useTopicSubscription(userTasksDest, (e) => appendLog("userTasks", e));

  const subscribedTopics = [
    stockDest && { label: "stock", dest: stockDest },
    locationsDest && { label: "locations", dest: locationsDest },
    approvalsDest && { label: "approvals", dest: approvalsDest },
    integrationsDest && { label: "integrations", dest: integrationsDest },
    tasksDest && { label: "tasks", dest: tasksDest },
    operatorsDest && { label: "operators", dest: operatorsDest },
    userApprovalsDest && { label: "userApprovals", dest: userApprovalsDest },
    userTasksDest && { label: "userTasks", dest: userTasksDest },
  ].filter(Boolean) as { label: string; dest: string }[];

  return (
    <div>
      <PageHeader
        title={t("rt.title")}
        subtitle={t("rt.subtitle")}
        actions={
          <div style={{ display: "flex", gap: 8 }}>
            <button
              className="btn btn-primary"
              onClick={() => void triggerTestEvent()}
              disabled={offline || !connected || companyId == null || locationId == null || triggering}
              title={t("rt.triggerHint")}
            >
              <Zap size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
              {triggering ? t("rt.triggering") : t("rt.triggerButton")}
            </button>
            <button className="btn btn-secondary" onClick={() => { setLogs([]); setTotalCount(0); }} disabled={logs.length === 0}>
              <Trash2 size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />
              {t("rt.clear")}
            </button>
          </div>
        }
      />

      {offline && <InfoBanner message={t("rt.offlineWarning")} />}
      <ErrorBanner message={triggerError} />
      <SuccessBanner message={triggerSuccess} />

      <div style={{ display: "flex", gap: 16, marginBottom: 20, flexWrap: "wrap", alignItems: "center" }}>
        <div
          className="glass-card"
          style={{
            padding: "14px 20px",
            display: "flex",
            alignItems: "center",
            gap: 10,
            minWidth: 180,
          }}
        >
          <Radio size={20} color={connected ? "var(--neon-green)" : "var(--neon-red)"} />
          <div>
            <div style={{ fontSize: "0.72rem", color: "var(--text-muted)", textTransform: "uppercase" }}>
              {t("rt.connectionStatus")}
            </div>
            <span
              className={`badge ${connected ? "badge-green" : "badge-red"}`}
              style={{ fontSize: "0.75rem", marginTop: 4 }}
            >
              {connected ? t("rt.connected") : t("rt.disconnected")}
            </span>
          </div>
        </div>

        <StatCard label={t("rt.messageCount")} value={totalCount} color="var(--neon-purple)" />
        <StatCard label={t("rt.userId")} value={userId ?? "—"} />
        <StatCard label={t("rt.companyId")} value={companyId ?? "—"} />
        <StatCard label={t("rt.locationId")} value={locationId ?? "—"} />
      </div>

      <Section title={t("rt.subscribedTopics")}>
        {subscribedTopics.length === 0 ? (
          <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>{t("rt.noTopics")}</p>
        ) : (
          <ul style={{ margin: 0, paddingLeft: 20, fontSize: "0.82rem", fontFamily: "monospace", color: "var(--text-secondary)" }}>
            {subscribedTopics.map(({ label, dest }) => (
              <li key={label} style={{ marginBottom: 6 }}>
                <strong style={{ color: "var(--neon-blue)" }}>{label}</strong>
                {" → "}
                {dest}
              </li>
            ))}
          </ul>
        )}
      </Section>

      <InfoBanner message={t("rt.howToTrigger")} />

      <Section title={t("rt.messageLog")}>
        <DataTable
          screenCode="REALTIME_TEST_LIST"
          rowKey={(row) => row.id}
          rows={logs}
          emptyMessage={t("rt.noMessages")}
          fallbackColumns={REALTIME_TEST_FALLBACK}
          renderers={{
            time: (row) => (
              <span style={{ whiteSpace: "nowrap", fontSize: "0.78rem" }}>{formatDateTime(row.receivedAt)}</span>
            ),
            topic: (row) => (
              <>
                <span className="badge badge-blue" style={{ fontSize: "0.68rem" }}>
                  {row.topicLabel}
                </span>
                <strong style={{ display: "block", fontSize: "0.82rem", marginTop: 4 }}>{row.eventType}</strong>
              </>
            ),
            payload: (row) => (
              <span
                style={{
                  fontFamily: "monospace",
                  fontSize: "0.72rem",
                  color: "var(--text-muted)",
                  maxWidth: 360,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  display: "inline-block",
                }}
                title={row.payloadPreview}
              >
                {row.payloadPreview}
              </span>
            ),
          }}
        />
      </Section>
    </div>
  );
}
