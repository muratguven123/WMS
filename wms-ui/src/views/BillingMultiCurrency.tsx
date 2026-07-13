/**
 * İş İsterleri 8-9 — Çoklu Para Birimi Faturalama
 * Faturalar sekmesi: billing-service | Finans sekmesi: finance-service
 */

import { useState, type CSSProperties } from "react";
import { describeError } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner, InfoBanner, PageHeader, SuccessBanner } from "../components/common";
import { FinanceTab } from "./billing/FinanceTab";
import { InvoiceTab, mapBillingError } from "./billing/InvoiceTab";

type BillingTab = "invoices" | "finance";

export interface BillingMultiCurrencyProps {
  activeLocationId?: number | "";
}

export function BillingMultiCurrency({ activeLocationId }: BillingMultiCurrencyProps) {
  const { t } = useI18n();
  const [tab, setTab] = useState<BillingTab>("invoices");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const tenantReady = Boolean(activeLocationId);

  const run = async (fn: () => Promise<string | void>, successMsg?: string) => {
    setError(null);
    setSuccess(null);
    try {
      const msg = await fn();
      if (msg) setSuccess(msg);
      else if (successMsg) setSuccess(successMsg);
    } catch (e) {
      const msg = describeError(e);
      if (tab === "finance" && /curren|para birimi|not allowed|izin/i.test(msg)) {
        setError(`${t("bill.currencyRejected")} · ${msg}`);
      } else if (tab === "invoices") {
        setError(mapBillingError(e, t));
      } else {
        setError(msg);
      }
    }
  };

  const tabStyle = (active: boolean): CSSProperties => ({
    padding: "10px 20px",
    border: "none",
    borderBottom: active ? "2px solid var(--neon-green)" : "2px solid transparent",
    background: "transparent",
    color: active ? "var(--neon-green)" : "var(--text-muted)",
    fontWeight: active ? 600 : 400,
    cursor: "pointer",
    fontSize: "0.9rem",
  });

  return (
    <div>
      <PageHeader title={t("bill.title")} subtitle={t("bill.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />
      <InfoBanner message={t("bill.infoBanner")} />

      <div style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--border-subtle)", marginBottom: 20 }}>
        <button type="button" style={tabStyle(tab === "invoices")} onClick={() => setTab("invoices")}>
          {t("bill.tab.invoices")}
        </button>
        <button type="button" style={tabStyle(tab === "finance")} onClick={() => setTab("finance")}>
          {t("bill.tab.finance")}
        </button>
      </div>

      {tab === "invoices" ? (
        <InvoiceTab activeLocationId={activeLocationId} tenantReady={tenantReady} run={run} />
      ) : (
        <FinanceTab run={run} />
      )}
    </div>
  );
}
