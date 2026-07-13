import { useState, useEffect, useCallback } from "react";
import { Layout } from "./components/Layout";
import { Dashboard } from "./views/Dashboard";
import { RackView } from "./views/RackView";
import { InboundPutaway } from "./views/InboundPutaway";
import { InventoryTransfer } from "./views/InventoryTransfer";
import { PickingRoute } from "./views/PickingRoute";
import { ShippingOutbound } from "./views/ShippingOutbound";
import { LanguageManagement } from "./views/LanguageManagement";
import { LocalizationSettings } from "./views/LocalizationSettings";
import { AddressMaster } from "./views/AddressMaster";
import { AddressTemplateConfig } from "./views/AddressTemplateConfig";
import { CurrencyExchange } from "./views/CurrencyExchange";
import { BillingMultiCurrency } from "./views/BillingMultiCurrency";
import { TaxManagement } from "./views/TaxManagement";
import { OrgHierarchy } from "./views/OrgHierarchy";
import { useI18n } from "./i18n/I18nContext";
import { WorkflowConfig } from "./views/WorkflowConfig";
import { ApprovalQueue } from "./views/ApprovalQueue";
import { DynamicFieldRules } from "./views/DynamicFieldRules";
import { IntegrationMonitor } from "./views/IntegrationMonitor";
import { AuditLog } from "./views/AuditLog";
import { UserManagement } from "./views/UserManagement";
import { CountryManagement } from "./views/CountryManagement";
import { RealtimeTest } from "./views/RealtimeTest";
import { tenantContext } from "./api/wms-api-client";
import { canAccessView } from "./auth/roles";
import { DEMO_COMPANIES, DEMO_LOCATIONS, useOrgData } from "./hooks/useOrgData";
import { StompProvider } from "./realtime/StompProvider";

function App() {
  const { t } = useI18n();
  const [currentView, setCurrentView] = useState<string>("dashboard");
  const [activeCompanyId, setActiveCompanyId] = useState<number | "">("");
  const [activeLocationId, setActiveLocationId] = useState<number | "">("");
  const [showForbiddenModal, setShowForbiddenModal] = useState<boolean>(false);
  const [highlightedBinId, setHighlightedBinId] = useState<string>("");
  const [tenantReady, setTenantReady] = useState(false);

  const { companies, locations, loadLocations, setLocations } = useOrgData();

  const pickLocationId = (
    locs: { id: number }[],
    preferred: number | null | undefined,
  ): number => {
    if (preferred != null && locs.some((l) => l.id === preferred)) return preferred;
    return locs[0]?.id ?? 0;
  };

  const applyTenant = useCallback((companyId: number, locationId: number) => {
    setActiveCompanyId(companyId);
    setActiveLocationId(locationId);
    tenantContext.set(companyId, locationId);
  }, []);

  const handleCompanyChange = useCallback(
    async (newCompanyId: number) => {
      try {
        const locs = await loadLocations(newCompanyId);
        const newLocationId = locs[0]?.id ?? 0;
        if (newLocationId) applyTenant(newCompanyId, newLocationId);
      } catch {
        const demoLocs = DEMO_LOCATIONS.filter((l) => l.companyId === newCompanyId);
        setLocations(demoLocs);
        const fallback = demoLocs[0]?.id;
        if (fallback) applyTenant(newCompanyId, fallback);
      }
    },
    [applyTenant, loadLocations, setLocations],
  );

  const handleLocationsChanged = useCallback(
    async (companyId: number) => {
      await loadLocations(companyId);
    },
    [loadLocations],
  );

  useEffect(() => {
    const handleTenantForbidden = (event: Event) => {
      console.warn("Global Event wms:tenant-forbidden triggered:", (event as CustomEvent).detail);
      setShowForbiddenModal(true);
    };
    window.addEventListener("wms:tenant-forbidden", handleTenantForbidden);
    return () => window.removeEventListener("wms:tenant-forbidden", handleTenantForbidden);
  }, []);

  useEffect(() => {
    if (companies.length === 0 || tenantReady) return;

    const savedCompany = tenantContext.getCompanyId();
    const savedLocation = tenantContext.getLocationId();

    const bootstrap = async () => {
      const companyId =
        savedCompany != null && companies.some((c) => c.id === savedCompany)
          ? savedCompany
          : companies[0].id;

      try {
        const locs = await loadLocations(companyId);
        const locationId = pickLocationId(locs, savedLocation);
        if (locationId) applyTenant(companyId, locationId);
      } catch {
        const demoLocs = DEMO_LOCATIONS.filter((l) => l.companyId === companyId);
        setLocations(demoLocs);
        const locationId = pickLocationId(demoLocs, savedLocation);
        if (locationId) applyTenant(companyId, locationId);
      }
      setTenantReady(true);
    };

    void bootstrap();
  }, [companies, tenantReady, loadLocations, applyTenant, setLocations]);

  useEffect(() => {
    const handleNavigate = (event: Event) => {
      const view = (event as CustomEvent<{ view?: string }>).detail?.view;
      if (view && canAccessView(view)) {
        setCurrentView(view);
      }
    };
    window.addEventListener("wms:navigate", handleNavigate);
    return () => window.removeEventListener("wms:navigate", handleNavigate);
  }, []);

  useEffect(() => {
    if (!canAccessView(currentView)) {
      setCurrentView("dashboard");
    }
  }, [currentView]);

  const navigateToView = useCallback((viewId: string) => {
    if (canAccessView(viewId)) {
      setCurrentView(viewId);
    } else {
      setCurrentView("dashboard");
    }
  }, []);

  const handleNavigateToMap = (binId: string) => {
    if (!canAccessView("rack-view")) {
      setCurrentView("dashboard");
      return;
    }
    setHighlightedBinId(binId);
    setCurrentView("rack-view");
  };

  const handleNavigateToShipping = (pickingListId: number) => {
    if (!canAccessView("shipping")) {
      setCurrentView("dashboard");
      return;
    }
    console.log(`Navigating to shipping for picking list: ${pickingListId}`);
    setCurrentView("shipping");
  };

  const renderDashboard = () => (
    <Dashboard
      activeCompanyId={activeCompanyId}
      activeLocationId={activeLocationId}
      onNavigate={navigateToView}
    />
  );

  const renderViewContent = () => {
    if (!tenantReady) {
      return <div style={{ color: "var(--text-muted)" }}>{t("app.tenantLoading")}</div>;
    }

    if (!canAccessView(currentView)) {
      return renderDashboard();
    }

    switch (currentView) {
      case "dashboard":
        return renderDashboard();
      case "rack-view":
        return (
          <RackView
            highlightedBinId={highlightedBinId}
            clearHighlight={() => setHighlightedBinId("")}
            activeLocationId={activeLocationId}
          />
        );
      case "inbound":
        return <InboundPutaway onNavigateToMap={handleNavigateToMap} activeLocationId={activeLocationId} />;
      case "transfer":
        return <InventoryTransfer activeLocationId={activeLocationId} />;
      case "picking":
        return <PickingRoute onNavigateToShipping={handleNavigateToShipping} activeLocationId={activeLocationId} />;
      case "shipping":
        return <ShippingOutbound activeLocationId={activeLocationId} />;
      case "languages":
        return <LanguageManagement />;
      case "localization-settings":
        return <LocalizationSettings />;
      case "address-master":
        return <AddressMaster />;
      case "address-template-config":
        return <AddressTemplateConfig />;
      case "country-management":
        return <CountryManagement />;
      case "currency":
        return <CurrencyExchange />;
      case "billing":
        return <BillingMultiCurrency activeLocationId={activeLocationId} />;
      case "tax":
        return <TaxManagement activeLocationId={activeLocationId} />;
      case "org-hierarchy":
        return (
          <OrgHierarchy
            activeCompanyId={activeCompanyId}
            activeLocationId={activeLocationId}
            companies={companies}
            locations={locations}
            setActiveCompanyId={setActiveCompanyId}
            setActiveLocationId={setActiveLocationId}
            onCompanyChange={handleCompanyChange}
            applyTenant={applyTenant}
            onLocationsChanged={handleLocationsChanged}
          />
        );
      case "workflow-config":
        return <WorkflowConfig activeLocationId={activeLocationId} />;
      case "approval-queue":
        return (
          <ApprovalQueue
            activeCompanyId={activeCompanyId}
            activeLocationId={activeLocationId}
          />
        );
      case "dynamic-ui":
        return <DynamicFieldRules />;
      case "integration-monitor":
        return <IntegrationMonitor activeLocationId={activeLocationId} />;
      case "audit-log":
        return <AuditLog activeLocationId={activeLocationId} />;
      case "realtime-test":
        return <RealtimeTest />;
      case "users":
        return <UserManagement />;
      default:
        return renderDashboard();
    }
  };

  const tenantCompanyId = activeCompanyId === "" ? null : activeCompanyId;
  const tenantLocationId = activeLocationId === "" ? null : activeLocationId;

  return (
    <StompProvider companyId={tenantCompanyId} locationId={tenantLocationId}>
      <Layout
      currentView={currentView}
      setCurrentView={navigateToView}
      activeCompanyId={activeCompanyId}
      setActiveCompanyId={setActiveCompanyId}
      activeLocationId={activeLocationId}
      setActiveLocationId={setActiveLocationId}
      showForbiddenModal={showForbiddenModal}
      setShowForbiddenModal={setShowForbiddenModal}
      companies={companies.length > 0 ? companies : DEMO_COMPANIES}
      locations={locations.length > 0 ? locations : DEMO_LOCATIONS}
      onCompanyChange={handleCompanyChange}
    >
      {renderViewContent()}
    </Layout>
    </StompProvider>
  );
}

export default App;
