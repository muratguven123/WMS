import { useState } from "react";
import {
  TrendingUp,
  AlertTriangle,
  Layers,
  Inbox,
  ArrowRight,
  ShieldAlert,
  UserCheck,
  ArrowLeftRight
} from "lucide-react";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { useI18n } from "../i18n/I18nContext";
import { FEATURES, isFeatureEnabled } from "../auth/roles";

interface DashboardProps {
  activeCompanyId: number | "";
  activeLocationId: number | "";
  onNavigate: (view: string) => void;
}

interface OperatorStatus {
  operatorUserId: string;
  status: string;
  pickingListId?: string;
}

export const Dashboard: React.FC<DashboardProps> = ({
  activeCompanyId,
  activeLocationId,
  onNavigate
}) => {
  const { t } = useI18n();
  const operationsEnabled = isFeatureEnabled(FEATURES.OPERATIONS);
  const [operators, setOperators] = useState<OperatorStatus[]>(() =>
    operationsEnabled ? [{ operatorUserId: "demo-op-1", status: "IN_PROGRESS", pickingListId: "—" }] : [],
  );

  useTopicSubscription(
    activeCompanyId && activeLocationId && isFeatureEnabled(FEATURES.REALTIME)
      ? stompDestinations.operators(activeCompanyId, activeLocationId)
      : null,
    (event) => {
      if (event.eventType !== "OPERATOR_STATUS_UPDATED") return;
      const p = event.payload;
      const operatorUserId = String(p.operatorUserId ?? "");
      if (!operatorUserId) return;
      setOperators((prev) => {
        const rest = prev.filter((o) => o.operatorUserId !== operatorUserId);
        return [
          {
            operatorUserId,
            status: String(p.status ?? "IDLE"),
            pickingListId: p.pickingListId ? String(p.pickingListId) : undefined,
          },
          ...rest,
        ].slice(0, 8);
      });
    },
  );

  const localizationKpis = [
    {
      title: t("nav.languages") || "Languages",
      value: "2",
      sub: t("app.activeLanguagesSub") || "Türkçe & English",
      icon: Layers,
      color: "var(--neon-blue)",
      shadow: "var(--shadow-neon-blue)",
    },
    {
      title: t("nav.currency") || "Currencies",
      value: "6",
      sub: t("app.activeCurrenciesSub") || "TRY, USD, EUR, GBP, AED, SAR",
      icon: ArrowLeftRight,
      color: "var(--neon-purple)",
      shadow: "var(--shadow-neon-purple)",
    },
    {
      title: t("nav.tax") || "Tax Rules",
      value: "3",
      sub: t("app.activeTaxRulesSub") || "KDV, VAT, GST",
      icon: TrendingUp,
      color: "var(--neon-green)",
      shadow: "var(--shadow-neon-green)",
    },
    {
      title: t("nav.addressMaster") || "Address Formats",
      value: "2",
      sub: t("app.activeAddressSub") || "Türkiye (TR) & USA (US)",
      icon: AlertTriangle,
      color: "var(--neon-orange)",
      shadow: "var(--shadow-neon-orange)",
    },
  ];

  // Mock data for dashboard
  const kpis = isFeatureEnabled(FEATURES.OPERATIONS) ? [
    {
      title: t("ops.dash.kpi.occupancy"),
      value: "68.4%",
      sub: t("ops.dash.kpi.occupancySub"),
      icon: Layers,
      color: "var(--neon-blue)",
      shadow: "var(--shadow-neon-blue)",
    },
    {
      title: t("ops.dash.kpi.inbound"),
      value: t("ops.dash.kpi.inboundVal"),
      sub: t("ops.dash.kpi.inboundSub"),
      icon: Inbox,
      color: "var(--neon-purple)",
      shadow: "var(--shadow-neon-purple)",
    },
    {
      title: t("ops.dash.kpi.picking"),
      value: t("ops.dash.kpi.pickingVal"),
      sub: t("ops.dash.kpi.pickingSub"),
      icon: TrendingUp,
      color: "var(--neon-green)",
      shadow: "var(--shadow-neon-green)",
    },
    {
      title: t("ops.dash.kpi.blocked"),
      value: t("ops.dash.kpi.blockedVal"),
      sub: t("ops.dash.kpi.blockedSub"),
      icon: AlertTriangle,
      color: "var(--neon-orange)",
      shadow: "var(--shadow-neon-orange)",
    },
  ] : localizationKpis;

  const localizationLinks = [
    {
      id: "languages",
      title: t("nav.languages") || "Language Management",
      desc: t("app.dash.langDesc") || "Translate menus, labels, and reports on-the-fly.",
      icon: Layers,
      color: "var(--neon-blue)",
    },
    {
      id: "address-master",
      title: t("nav.addressMaster") || "Address Master",
      desc: t("app.dash.addressDesc") || "Configure country-specific address structures.",
      icon: Inbox,
      color: "var(--neon-purple)",
    },
    {
      id: "currency",
      title: t("nav.currency") || "Currency & Rates",
      desc: t("app.dash.currencyDesc") || "Monitor exchange rates and sync daily TCMB values.",
      icon: ArrowLeftRight,
      color: "var(--neon-orange)",
    },
    {
      id: "tax",
      title: t("nav.tax") || "Tax Configuration",
      desc: t("app.dash.taxDesc") || "Set country/location tax rates and calculation strategies.",
      icon: TrendingUp,
      color: "var(--neon-green)",
    },
  ];

  const recentAlerts = [
    { id: 1, message: t("ops.dash.alert1"), time: t("ops.dash.time5m"), type: "orange" },
    { id: 2, message: t("ops.dash.alert2"), time: t("ops.dash.time12m"), type: "green" },
    { id: 3, message: t("ops.dash.alert3"), time: t("ops.dash.time30m"), type: "blue" },
  ];

  // Function to simulate a 403 Forbidden response event
  const handleSimulate403 = () => {
    console.log("Simulating 403 Forbidden event...");
    window.dispatchEvent(
      new CustomEvent("wms:tenant-forbidden", {
        detail: {
          companyId: activeCompanyId,
          locationId: activeLocationId,
          url: "/api/inventory/locations/protected-endpoint",
        },
      })
    );
  };

  return (
    <div style={styles.container}>
      {/* Top Welcome Banner */}
      <div className="glass-card" style={styles.welcomeBanner}>
        <div>
          <h1 style={styles.welcomeTitle}>
            {operationsEnabled ? t("ops.dash.welcome") : t("app.dash.welcome")}
          </h1>
          <p style={styles.welcomeSub}>
            {t("common.tenant")}: <strong style={{ color: "var(--neon-blue)" }}>{activeCompanyId}</strong> &nbsp;|&nbsp;
            {t("common.locationLabel")}: <strong style={{ color: "var(--neon-green)" }}>{activeLocationId}</strong>
          </p>
        </div>
        {operationsEnabled && (
          <button className="btn btn-primary" onClick={handleSimulate403}>
            <ShieldAlert size={16} />
            {t("ops.dash.simulate403")}
          </button>
        )}
      </div>

      {/* KPI GRID */}
      <div className="dashboard-grid">
        {kpis.map((kpi, idx) => {
          const Icon = kpi.icon;
          return (
            <div
              key={idx}
              className="glass-card"
              style={{
                ...styles.kpiCard,
                borderColor: `rgba(${idx === 0 ? "0,210,255" : idx === 1 ? "217,70,239" : idx === 2 ? "0,245,155" : "255,159,0"}, 0.15)`,
              }}
            >
              <div style={styles.kpiHeader}>
                <span style={styles.kpiTitle}>{kpi.title}</span>
                <div style={{ ...styles.kpiIconWrapper, background: kpi.color + "15", border: "1px solid " + kpi.color + "40", boxShadow: kpi.shadow }}>
                  <Icon size={20} color={kpi.color} />
                </div>
              </div>
              <div style={styles.kpiValue}>{kpi.value}</div>
              <div style={styles.kpiSub}>{kpi.sub}</div>
            </div>
          );
        })}
      </div>

      {/* TWO PANEL SPLIT */}
      <div 
        className="layout-split"
        style={!isFeatureEnabled(FEATURES.OPERATIONS) ? { gridTemplateColumns: "1fr" } : undefined}
      >
        {/* Left Panel: Quick Links & Actions */}
        <div className="glass-card" style={styles.panelCard}>
          <h3 style={styles.panelTitle}>
            {isFeatureEnabled(FEATURES.OPERATIONS) ? t("ops.dash.quickOps") : (t("ops.dash.quickConfigs") || "Quick Configurations")}
          </h3>
          <div style={styles.quickLinksGrid}>
            {isFeatureEnabled(FEATURES.OPERATIONS) ? (
              <>
                <div style={styles.quickLinkCard} className="quick-link-card" onClick={() => onNavigate("rack-view")}>
                  <Layers size={32} color="var(--neon-blue)" />
                  <h4>{t("ops.dash.rackTitle")}</h4>
                  <p>{t("ops.dash.rackDesc")}</p>
                  <span style={styles.linkArrow}>{t("common.go")} <ArrowRight size={14} /></span>
                </div>
                <div style={styles.quickLinkCard} className="quick-link-card" onClick={() => onNavigate("inbound")}>
                  <Inbox size={32} color="var(--neon-purple)" />
                  <h4>{t("ops.dash.inboundTitle")}</h4>
                  <p>{t("ops.dash.inboundDesc")}</p>
                  <span style={styles.linkArrow}>{t("common.go")} <ArrowRight size={14} /></span>
                </div>
                <div style={styles.quickLinkCard} className="quick-link-card" onClick={() => onNavigate("transfer")}>
                  <ArrowLeftRight size={32} color="var(--neon-orange)" />
                  <h4>{t("ops.dash.transferTitle")}</h4>
                  <p>{t("ops.dash.transferDesc")}</p>
                  <span style={styles.linkArrow}>{t("common.go")} <ArrowRight size={14} /></span>
                </div>
                <div style={styles.quickLinkCard} className="quick-link-card" onClick={() => onNavigate("picking")}>
                  <TrendingUp size={32} color="var(--neon-green)" />
                  <h4>{t("ops.dash.pickingTitle")}</h4>
                  <p>{t("ops.dash.pickingDesc")}</p>
                  <span style={styles.linkArrow}>{t("common.go")} <ArrowRight size={14} /></span>
                </div>
              </>
            ) : (
              localizationLinks.map((link) => {
                const LinkIcon = link.icon;
                return (
                  <div key={link.id} style={styles.quickLinkCard} className="quick-link-card" onClick={() => onNavigate(link.id)}>
                    <LinkIcon size={32} color={link.color} />
                    <h4>{link.title}</h4>
                    <p>{link.desc}</p>
                    <span style={styles.linkArrow}>{t("common.go")} <ArrowRight size={14} /></span>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {isFeatureEnabled(FEATURES.OPERATIONS) && (
          <div className="glass-card" style={styles.panelCard}>
            <h3 style={styles.panelTitle}>{t("ops.dash.systemLog")}</h3>
            <div style={styles.streamList}>
              {recentAlerts.map((alert) => (
                <div key={alert.id} style={styles.streamItem}>
                  <div style={{
                    ...styles.streamMarker,
                    backgroundColor: alert.type === "green" ? "var(--neon-green)" : alert.type === "orange" ? "var(--neon-orange)" : "var(--neon-blue)",
                    boxShadow: alert.type === "green" ? "var(--shadow-neon-green)" : alert.type === "orange" ? "var(--shadow-neon-orange)" : "var(--shadow-neon-blue)"
                  }} />
                  <div style={styles.streamBody}>
                    <p style={styles.streamText}>{alert.message}</p>
                    <span style={styles.streamTime}>{alert.time}</span>
                  </div>
                </div>
              ))}
            </div>

            <div style={styles.workersCard}>
              <h4 style={{ ...styles.panelTitle, fontSize: "0.95rem", marginBottom: "12px" }}>{t("ops.dash.activeWorkers")}</h4>
              {operators.length === 0 ? (
                <p style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>{t("ops.dash.noOperators")}</p>
              ) : (
                operators.map((op) => (
                  <div key={op.operatorUserId} style={styles.workerItem}>
                    <UserCheck size={16} color="var(--neon-green)" />
                    <span>
                      {op.operatorUserId.slice(0, 8)}… ({op.status}
                      {op.pickingListId ? ` — ${op.pickingListId.slice(0, 8)}` : ""})
                    </span>
                    <span className={`badge ${op.status === "IN_PROGRESS" ? "badge-green" : "badge-orange"}`} style={{ fontSize: "0.55rem" }}>
                      {op.status}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  welcomeBanner: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "25px",
    background: "linear-gradient(135deg, rgba(14,19,34,0.8) 0%, rgba(27,35,54,0.4) 100%)",
  },
  welcomeTitle: {
    fontSize: "1.6rem",
    fontWeight: 600,
    marginBottom: "5px",
    color: "var(--text-primary)",
    textAlign: "left",
  },
  welcomeSub: {
    color: "var(--text-secondary)",
    fontSize: "0.95rem",
    textAlign: "left",
  },
  kpiCard: {
    display: "flex",
    flexDirection: "column",
    position: "relative",
    padding: "20px",
  },
  kpiHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "10px",
  },
  kpiTitle: {
    fontSize: "0.85rem",
    fontWeight: 500,
    color: "var(--text-secondary)",
    textTransform: "uppercase",
    letterSpacing: "0.5px",
  },
  kpiIconWrapper: {
    width: "36px",
    height: "36px",
    borderRadius: "8px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  kpiValue: {
    fontSize: "1.8rem",
    fontWeight: 700,
    color: "var(--text-primary)",
    marginBottom: "4px",
    textAlign: "left",
  },
  kpiSub: {
    fontSize: "0.8rem",
    color: "var(--text-muted)",
    textAlign: "left",
  },
  panelCard: {
    padding: "25px",
    display: "flex",
    flexDirection: "column",
    height: "100%",
  },
  panelTitle: {
    fontSize: "1.1rem",
    fontWeight: 600,
    color: "var(--text-primary)",
    marginBottom: "20px",
    textAlign: "left",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "10px",
  },
  quickLinksGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(2, 1fr)",
    gap: "15px",
    flexGrow: 1,
  },
  quickLinkCard: {
    background: "rgba(255, 255, 255, 0.02)",
    border: "1px solid var(--glass-border)",
    borderRadius: "10px",
    padding: "20px",
    cursor: "pointer",
    display: "flex",
    flexDirection: "column",
    alignItems: "flex-start",
    textAlign: "left",
    gap: "8px",
    transition: "var(--transition-smooth)",
  },
  linkArrow: {
    marginTop: "auto",
    fontSize: "0.8rem",
    color: "var(--neon-blue)",
    display: "flex",
    alignItems: "center",
    gap: "4px",
    fontWeight: 600,
  },
  streamList: {
    display: "flex",
    flexDirection: "column",
    gap: "16px",
    marginBottom: "20px",
  },
  streamItem: {
    display: "flex",
    gap: "12px",
    alignItems: "flex-start",
    textAlign: "left",
  },
  streamMarker: {
    width: "8px",
    height: "8px",
    borderRadius: "50%",
    marginTop: "6px",
    flexShrink: 0,
  },
  streamBody: {
    display: "flex",
    flexDirection: "column",
  },
  streamText: {
    fontSize: "0.85rem",
    color: "var(--text-primary)",
    lineHeight: "1.4",
  },
  streamTime: {
    fontSize: "0.75rem",
    color: "var(--text-muted)",
    marginTop: "2px",
  },
  workersCard: {
    background: "rgba(0, 0, 0, 0.15)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "15px",
    marginTop: "auto",
  },
  workerItem: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
    padding: "8px 0",
    borderBottom: "1px solid rgba(255, 255, 255, 0.03)",
  },
};
// Add hover behaviors
if (typeof document !== 'undefined') {
  const styleEl = document.createElement("style");
  styleEl.innerHTML = `
    .quick-link-card:hover {
      background: rgba(255, 255, 255, 0.05);
      border-color: var(--neon-blue);
      transform: translateY(-2px);
      box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
    }
  `;
  document.head.appendChild(styleEl);
}
