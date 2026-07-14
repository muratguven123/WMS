import React, { useState, useEffect } from "react";
import {
  LayoutDashboard,
  Grid3X3,
  ArrowDownLeft,
  ArrowLeftRight,
  ListTodo,
  Truck,
  LogOut,
  Building2,
  MapPin,
  User,
  ChevronLeft,
  ChevronRight,
  Network,
  Languages,
  Clock,
  MapPinned,
  Coins,
  Receipt,
  Percent,
  GitBranch,
  SlidersHorizontal,
  Activity,
  ScrollText,
  Globe,
  ClipboardCheck,
  UserCog,
  Radio,
  Settings2,
  Factory,
} from "lucide-react";
import { keycloak, tenantContext, logoutUser } from "../api/wms-api-client";
import { canAccessView, getRealmRoles, isWmsAdmin, FEATURES, isFeatureEnabled } from "../auth/roles";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { isLangActive } from "../i18n/languageUtils";
import { useI18n } from "../i18n/I18nContext";
import { type TenantCompany, type TenantLocation } from "../hooks/useOrgData";
import { Dropdown } from "./Dropdown";

export type { TenantCompany as Company, TenantLocation as Location };

interface LayoutProps {
  currentView: string;
  setCurrentView: (view: string) => void;
  children: React.ReactNode;
  activeCompanyId: number | "";
  setActiveCompanyId: (id: number | "") => void;
  activeLocationId: number | "";
  setActiveLocationId: (id: number | "") => void;
  showForbiddenModal: boolean;
  setShowForbiddenModal: (show: boolean) => void;
  companies: TenantCompany[];
  locations: TenantLocation[];
  onCompanyChange: (companyId: number) => void;
}

export const Layout: React.FC<LayoutProps> = ({
  currentView,
  setCurrentView,
  children,
  activeCompanyId,
  setActiveCompanyId: _setActiveCompanyId,
  activeLocationId,
  setActiveLocationId,
  showForbiddenModal,
  setShowForbiddenModal,
  companies,
  locations,
  onCompanyChange,
}) => {
  const { t, lang, setLang, languages } = useI18n();
  const [isSidebarExpanded, setIsSidebarExpanded] = useState(true);
  const [userName, setUserName] = useState("Murat Demir");
  const [offlineMode, setOfflineMode] = useState(false);
  const [approvalBadge, setApprovalBadge] = useState(0);

  useTopicSubscription(
    activeCompanyId && isFeatureEnabled(FEATURES.REALTIME) ? stompDestinations.approvals(activeCompanyId) : null,
    (event) => {
      if (event.eventType === "APPROVAL_CREATED") {
        setApprovalBadge((n) => n + 1);
      }
      if (event.eventType === "APPROVAL_RESOLVED") {
        setApprovalBadge((n) => Math.max(0, n - 1));
      }
    },
  );

  useEffect(() => {
    const onCount = (e: Event) => {
      const detail = (e as CustomEvent<number>).detail;
      if (typeof detail === "number") setApprovalBadge(detail);
    };
    window.addEventListener("wms:approval-count", onCount);
    return () => window.removeEventListener("wms:approval-count", onCount);
  }, []);

  useEffect(() => {
    // Determine username from keycloak or fallback
    if (keycloak.tokenParsed) {
      setUserName(
        keycloak.tokenParsed.name ||
        keycloak.tokenParsed.preferred_username ||
        "WMS Operator"
      );
    }
    if ((window as any).wmsOfflineMode) {
      setOfflineMode(true);
    }
  }, []);

  const filteredLocations = locations.filter((loc) => loc.companyId === activeCompanyId);

  const handleLogout = () => {
    if (offlineMode) {
      tenantContext.clear();
      window.location.reload();
    } else {
      void logoutUser().then(() => {
        window.dispatchEvent(new CustomEvent("wms:auth-required"));
      });
    }
  };

  const operationsEnabled = isFeatureEnabled(FEATURES.OPERATIONS);

  const navGroups = [
    operationsEnabled
      ? {
          label: t("nav.group.operations"),
          items: [
            { id: "dashboard", label: t("nav.dashboard"), icon: LayoutDashboard },
            { id: "rack-view", label: t("nav.rackView"), icon: Grid3X3 },
            { id: "inbound", label: t("nav.inbound"), icon: ArrowDownLeft },
            { id: "transfer", label: t("nav.transfer"), icon: ArrowLeftRight },
            { id: "picking", label: t("nav.picking"), icon: ListTodo },
            { id: "shipping", label: t("nav.shipping"), icon: Truck },
          ],
        }
      : {
          label: t("nav.group.general"),
          items: [{ id: "dashboard", label: t("nav.dashboard"), icon: LayoutDashboard }],
        },
    {
      label: t("nav.group.localization"),
      items: [
        { id: "languages", label: t("nav.languages"), icon: Languages },
        { id: "localization-settings", label: t("nav.localizationSettings"), icon: Clock },
        { id: "address-master", label: t("nav.addressMaster"), icon: MapPinned },
        { id: "address-template-config", label: t("nav.addressTemplateConfig"), icon: Settings2 },
      ],
    },
    {
      label: t("nav.group.finance"),
      items: [
        { id: "currency", label: t("nav.currency"), icon: Coins },
        { id: "billing", label: t("nav.billing"), icon: Receipt },
        { id: "tax", label: t("nav.tax"), icon: Percent },
      ],
    },
    {
      label: t("nav.group.admin"),
      items: [
        { id: "org-hierarchy", label: t("nav.orgHierarchy"), icon: Network },
        { id: "company-management", label: t("nav.companyMgmt"), icon: Factory },
        { id: "country-management", label: t("nav.countryMgmt"), icon: Globe },
        { id: "users", label: t("nav.users"), icon: UserCog },
        { id: "workflow-config", label: t("nav.workflowConfig"), icon: GitBranch },
        { id: "approval-queue", label: t("nav.approvalQueue"), icon: ClipboardCheck },
        { id: "dynamic-ui", label: t("nav.dynamicUi"), icon: SlidersHorizontal },
        { id: "integration-monitor", label: t("nav.integrationMonitor"), icon: Activity },
        { id: "realtime-test", label: t("nav.realtimeTest"), icon: Radio },
        { id: "audit-log", label: t("nav.auditLog"), icon: ScrollText },
      ],
    },
  ]
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => canAccessView(item.id)),
    }))
    .filter((group) => group.items.length > 0);

  const primaryRole = isWmsAdmin()
    ? "WMS Admin"
    : getRealmRoles().find((r) => !r.startsWith("default-roles-")) ?? "WMS Operator";
  const navItems = navGroups.flatMap((g) => g.items);

  return (
    <div style={layoutStyles.container}>
      {/* LEFT SIDEBAR */}
      <aside
        style={{
          ...layoutStyles.sidebar,
          width: isSidebarExpanded ? "var(--sidebar-width)" : "var(--sidebar-collapsed-width)",
        }}
      >
        <div style={layoutStyles.logoContainer}>
          <Network size={28} color="var(--neon-blue)" style={layoutStyles.logoIcon} />
          {isSidebarExpanded && <span style={layoutStyles.logoText}>WMS <span style={{ color: "var(--neon-blue)", fontWeight: 700 }}>PRO</span></span>}
        </div>

        <nav style={{ ...layoutStyles.nav, overflowY: "auto" }}>
          {navGroups.map((group) => (
            <div key={group.label}>
              {isSidebarExpanded && (
                <div style={layoutStyles.navGroupLabel}>{group.label}</div>
              )}
              {group.items.map((item) => {
                const Icon = item.icon;
                const isActive = currentView === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setCurrentView(item.id)}
                    style={{
                      ...layoutStyles.navItem,
                      width: "100%",
                      backgroundColor: isActive ? "rgba(255, 255, 255, 0.05)" : "transparent",
                      color: isActive ? "var(--text-primary)" : "var(--text-secondary)",
                      borderLeft: isActive ? "3px solid var(--neon-blue)" : "3px solid transparent",
                      justifyContent: isSidebarExpanded ? "flex-start" : "center",
                      paddingLeft: isSidebarExpanded ? "20px" : "0",
                    }}
                    title={item.label}
                  >
                    <Icon size={18} color={isActive ? "var(--neon-blue)" : "var(--text-secondary)"} />
                    {isSidebarExpanded && (
                      <span style={layoutStyles.navLabel}>
                        {item.label}
                        {item.id === "approval-queue" && approvalBadge > 0 && (
                          <span style={layoutStyles.navBadge}>{approvalBadge}</span>
                        )}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <button
          onClick={() => setIsSidebarExpanded(!isSidebarExpanded)}
          style={layoutStyles.collapseToggle}
        >
          {isSidebarExpanded ? <ChevronLeft size={16} /> : <ChevronRight size={16} />}
        </button>
      </aside>

      {/* RIGHT SIDE MAIN CONTAINER */}
      <div
        style={{
          ...layoutStyles.mainContent,
          marginLeft: isSidebarExpanded ? "var(--sidebar-width)" : "var(--sidebar-collapsed-width)",
        }}
      >
        {/* HEADER */}
        <header style={layoutStyles.header}>
          <div style={layoutStyles.headerTitle}>
            <h2>{navItems.find((n) => n.id === currentView)?.label}</h2>
            {offlineMode && (
              <span className="badge badge-orange" style={{ marginLeft: "12px", fontSize: "0.65rem" }}>
                {t("layout.offlineDemo")}
              </span>
            )}
          </div>

          <div style={layoutStyles.headerActions}>
            {/* Tenant Selector Dropdowns */}
            <div style={layoutStyles.selectorsContainer}>
              <Dropdown
                value={activeCompanyId}
                onChange={(v) => onCompanyChange(Number(v))}
                icon={<Building2 size={16} color="var(--text-secondary)" />}
                minWidth={160}
                options={companies.map((c) => ({ value: c.id, label: c.name }))}
              />

              <Dropdown
                value={activeLocationId}
                onChange={(v) => {
                  const newLocationId = Number(v);
                  setActiveLocationId(newLocationId);
                  if (activeCompanyId !== "") {
                    tenantContext.set(activeCompanyId, newLocationId);
                  }
                }}
                icon={<MapPin size={16} color="var(--text-secondary)" />}
                minWidth={160}
                options={filteredLocations.map((l) => ({ value: l.id, label: l.name }))}
              />
            </div>

            {/* Dil Seçici — İş İsteri 2: on-the-fly dil değişimi */}
            <Dropdown
              value={lang}
              onChange={(v) => setLang(v)}
              icon={<Globe size={16} color="var(--neon-blue)" />}
              title={t("common.language")}
              minWidth={130}
              options={languages
                .filter((l) => isLangActive(l))
                .map((l) => ({ value: l.code, label: l.name }))}
            />

            {/* Profile Info */}
            <div style={layoutStyles.profileContainer}>
              <User size={18} color="var(--neon-blue)" />
              <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.2 }}>
                <span style={layoutStyles.profileName}>{userName}</span>
                <span style={{ fontSize: "0.72rem", color: "var(--text-muted)" }}>{primaryRole}</span>
              </div>
            </div>

            {/* Keycloak Logout */}
            <button
              onClick={handleLogout}
              style={layoutStyles.logoutBtn}
              title={t("common.logout")}
            >
              <LogOut size={18} />
            </button>
          </div>
        </header>

        {/* PAGE BODY */}
        <main style={layoutStyles.pageContainer}>{children}</main>
      </div>

      {/* HTTP 403 Forbidden Selection Modal */}
      {showForbiddenModal && (
        <div className="modal-backdrop">
          <div className="glass-card modal-wrapper" style={{ border: "1px solid var(--neon-orange)", boxShadow: "var(--shadow-neon-orange)" }}>
            <div style={{ textAlign: "center", padding: "20px 10px" }}>
              <Building2 size={48} color="var(--neon-orange)" style={{ marginBottom: "15px" }} />
              <h3 style={{ fontSize: "1.3rem", marginBottom: "10px", color: "var(--text-primary)" }}>
                {t("layout.forbiddenTitle")}
              </h3>
              <p style={{ color: "var(--text-secondary)", fontSize: "0.95rem", lineHeight: "1.5", marginBottom: "20px" }}>
                {t("layout.forbiddenBody")}
              </p>
              <div style={{ display: "flex", gap: "10px", justifyContent: "center" }}>
                <Dropdown
                  value={activeCompanyId}
                  onChange={(v) => {
                    onCompanyChange(Number(v));
                    setShowForbiddenModal(false);
                  }}
                  icon={<Building2 size={16} color="var(--text-secondary)" />}
                  placeholder="Firma Seçin..."
                  minWidth={0}
                  style={{ display: "block", width: "100%" }}
                  options={companies.map((c) => ({ value: c.id, label: c.name }))}
                />
              </div>
              <button
                className="btn btn-primary"
                style={{ marginTop: "20px", width: "100%" }}
                onClick={() => setShowForbiddenModal(false)}
              >
                {t("layout.forbiddenClose")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const layoutStyles: { [key: string]: React.CSSProperties } = {
  container: {
    display: "flex",
    width: "100%",
    minHeight: "100vh",
    backgroundColor: "var(--bg-primary)",
    position: "relative",
  },
  sidebar: {
    position: "fixed",
    top: 0,
    left: 0,
    bottom: 0,
    backgroundColor: "var(--bg-secondary)",
    borderRight: "1px solid var(--glass-border)",
    display: "flex",
    flexDirection: "column",
    zIndex: 100,
    transition: "var(--transition-smooth)",
    boxShadow: "5px 0 25px rgba(0, 0, 0, 0.2)",
  },
  logoContainer: {
    height: "var(--header-height)",
    display: "flex",
    alignItems: "center",
    padding: "0 20px",
    gap: "12px",
    borderBottom: "1px solid var(--glass-border)",
  },
  logoIcon: {
    filter: "drop-shadow(0 0 8px rgba(0, 210, 255, 0.4))",
  },
  logoText: {
    fontFamily: "var(--font-sans)",
    fontSize: "1.2rem",
    fontWeight: 600,
    letterSpacing: "1px",
    color: "var(--text-primary)",
  },
  nav: {
    display: "flex",
    flexDirection: "column",
    gap: "2px",
    padding: "10px 0 20px",
    flexGrow: 1,
  },
  navGroupLabel: {
    padding: "14px 20px 6px",
    fontSize: "0.65rem",
    fontWeight: 700,
    letterSpacing: "1.5px",
    textTransform: "uppercase",
    color: "var(--text-muted)",
  },
  navItem: {
    display: "flex",
    alignItems: "center",
    height: "42px",
    border: "none",
    fontSize: "0.95rem",
    fontWeight: 500,
    cursor: "pointer",
    transition: "var(--transition-smooth)",
  },
  navLabel: {
    marginLeft: "14px",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
    display: "inline-flex",
    alignItems: "center",
    gap: 8,
  },
  navBadge: {
    background: "var(--neon-orange)",
    color: "#0a0e1a",
    borderRadius: 10,
    fontSize: "0.7rem",
    fontWeight: 700,
    padding: "2px 7px",
    minWidth: 18,
    textAlign: "center" as const,
  },
  collapseToggle: {
    border: "none",
    background: "transparent",
    color: "var(--text-secondary)",
    cursor: "pointer",
    padding: "15px",
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    borderTop: "1px solid var(--glass-border)",
    transition: "var(--transition-smooth)",
  },
  mainContent: {
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
    minHeight: "100vh",
    transition: "var(--transition-smooth)",
  },
  header: {
    height: "var(--header-height)",
    backgroundColor: "var(--glass-bg)",
    backdropFilter: "var(--glass-blur)",
    borderBottom: "1px solid var(--glass-border)",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "0 30px",
    position: "sticky",
    top: 0,
    zIndex: 90,
  },
  headerTitle: {
    display: "flex",
    alignItems: "center",
  },
  headerActions: {
    display: "flex",
    alignItems: "center",
    gap: "20px",
  },
  selectorsContainer: {
    display: "flex",
    gap: "12px",
  },
  selectWrapper: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    background: "var(--bg-tertiary)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "6px 12px",
    transition: "var(--transition-smooth)",
  },
  headerSelect: {
    background: "transparent",
    border: "none",
    color: "var(--text-primary)",
    fontFamily: "var(--font-sans)",
    fontSize: "0.85rem",
    fontWeight: 500,
    outline: "none",
    cursor: "pointer",
  },
  profileContainer: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    borderLeft: "1px solid var(--glass-border)",
    paddingLeft: "20px",
  },
  profileName: {
    fontSize: "0.9rem",
    fontWeight: 500,
    color: "var(--text-secondary)",
  },
  logoutBtn: {
    background: "rgba(255, 51, 102, 0.1)",
    border: "1px solid rgba(255, 51, 102, 0.2)",
    color: "var(--neon-red)",
    width: "36px",
    height: "36px",
    borderRadius: "8px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    transition: "var(--transition-smooth)",
  },
  pageContainer: {
    padding: "30px",
    flexGrow: 1,
    overflowY: "auto",
  },
};
