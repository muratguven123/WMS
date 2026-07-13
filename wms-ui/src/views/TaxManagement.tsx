/**
 * İş İsteri 11 — Vergi Oranları ve Hesaplama
 */

import { useCallback, useEffect, useState } from "react";
import { Percent, History } from "lucide-react";
import {
  describeError,
  financeService,
  type TaxCalculateResponse,
  type TaxRateResponse,
  type TaxRateVersionResult,
} from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";

const TR_COUNTRY_ID = 1;

const TAX_RATE_LIST_COLUMNS = [
  fallbackCol("code", "columns.tax.code", 0),
  fallbackCol("rate", "columns.tax.rate", 1, { dataType: "NUMBER" }),
  fallbackCol("effectiveDate", "columns.tax.effectiveDate", 2, { dataType: "DATE" }),
  fallbackCol("status", "columns.common.status", 3),
];

const TAX_RATE_VERSION_LIST_COLUMNS = [
  fallbackCol("version", "columns.tax.version", 0, { dataType: "NUMBER" }),
  fallbackCol("rate", "columns.tax.rate", 1, { dataType: "NUMBER" }),
  fallbackCol("effectiveFrom", "columns.tax.effectiveFrom", 2, { dataType: "DATE" }),
];

interface TaxVersionRow {
  version: number;
  rate: number;
  effectiveFrom?: string;
}

export function TaxManagement({ activeLocationId }: { activeLocationId?: number | "" }) {
  const { t, formatNumber, formatDate } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [rates, setRates] = useState<TaxRateResponse[]>([]);
  const [ratesLoading, setRatesLoading] = useState(true);

  const [rateId, setRateId] = useState<number | "">("");
  const [newRate, setNewRate] = useState("");
  const [effDate, setEffDate] = useState(new Date().toISOString().slice(0, 10));
  const [versionResult, setVersionResult] = useState<TaxRateVersionResult | null>(null);

  const [base, setBase] = useState("1000");
  const [taxTypeCode, setTaxTypeCode] = useState("KDV");
  const [mode, setMode] = useState<"EXCLUSIVE" | "INCLUSIVE">("EXCLUSIVE");
  const [sim, setSim] = useState<TaxCalculateResponse | null>(null);
  const [simBusy, setSimBusy] = useState(false);

  const loadRates = useCallback(() => {
    if (!activeLocationId) {
      setRates([]);
      setRatesLoading(false);
      return;
    }
    setRatesLoading(true);
    financeService
      .listTaxRates({ active: true })
      .then((p) => setRates(p.content))
      .catch((e) => setError(describeError(e)))
      .finally(() => setRatesLoading(false));
  }, [activeLocationId]);

  useEffect(() => {
    loadRates();
  }, [loadRates]);

  const runSimulation = async () => {
    if (!activeLocationId) return;
    setSimBusy(true);
    setError(null);
    setSim(null);
    try {
      const amount = parseFloat(base.replace(",", "."));
      const result = await financeService.calculateTax({
        amount,
        taxTypeCode,
        mode,
        countryId: TR_COUNTRY_ID,
        locationId: activeLocationId,
      });
      setSim(result);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setSimBusy(false);
    }
  };

  const updateRate = async () => {
    setError(null);
    setSuccess(null);
    setVersionResult(null);
    if (rateId === "") {
      setError("Vergi oranı seçin.");
      return;
    }
    const rate = parseFloat(newRate.replace(",", "."));
    if (isNaN(rate) || rate < 0) {
      setError("Geçerli bir oran girin.");
      return;
    }
    try {
      const result = await financeService.updateTaxRate({
        taxRateId: rateId,
        newRate: rate,
        effectiveDate: effDate,
      });
      setVersionResult(result);
      setSuccess("Oran versiyonlandı.");
      loadRates();
    } catch (e) {
      setError(describeError(e));
    }
  };

  const VersionCard = ({ title, rate, color }: { title: string; rate?: TaxRateResponse; color: string }) => {
    if (!rate) return null;
    return (
      <div className="glass-card" style={{ padding: 16, border: `1px solid ${color}`, flex: 1, minWidth: 260 }}>
        <div style={{ fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: 1, color, marginBottom: 8 }}>{title}</div>
        <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--text-primary)", marginBottom: 6 }}>
          {rate.taxTypeCode} %{formatNumber(rate.rate, 2)}
        </div>
        <div style={{ fontSize: "0.8rem", color: "var(--text-secondary)" }}>
          {formatDate(rate.startDate)} → {rate.endDate ? formatDate(rate.endDate) : "∞"}
        </div>
      </div>
    );
  };

  return (
    <div>
      <PageHeader title={t("tax.title")} subtitle={t("tax.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />
      {!activeLocationId && <InfoBanner message={t("common.tenantRequired")} />}

      <Section title={t("tax.ratesTable")}>
        <DataTable
          screenCode="TAX_RATE_LIST"
          rowKey={(r) => r.id}
          rows={rates}
          loading={ratesLoading}
          fallbackColumns={TAX_RATE_LIST_COLUMNS}
          rowProps={(r) => ({ style: { cursor: "pointer" }, onClick: () => setRateId(r.id) })}
          renderers={{
            code: (r) => r.taxTypeCode,
            rate: (r) => formatNumber(r.rate, 2),
            effectiveDate: (r) => (r.startDate ? formatDate(r.startDate) : "—"),
            status: (r) => (
              <span className={`badge ${r.active ? "badge-green" : "badge-red"}`}>
                {r.active ? t("common.active") : t("common.passive")}
              </span>
            ),
          }}
        />
      </Section>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(380px, 1fr))", gap: 20 }}>
        <Section title={t("tax.updateVersioned")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("tax.rateId")}>
              <input
                className="form-input"
                placeholder="Tax rate ID"
                value={rateId === "" ? "" : String(rateId)}
                onChange={(e) => {
                  const v = e.target.value.trim();
                  setRateId(v === "" ? "" : Number(v));
                }}
              />
            </Field>
            <Field label={t("tax.newRate")}>
              <input className="form-input" placeholder="20" value={newRate} onChange={(e) => setNewRate(e.target.value)} />
            </Field>
            <Field label={t("tax.effectiveDate")}>
              <input className="form-input" type="date" value={effDate} onChange={(e) => setEffDate(e.target.value)} />
            </Field>
          </div>
          <button className="btn btn-primary" disabled={!rateId || !newRate} onClick={() => void updateRate()}>
            <History size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("tax.updateVersioned")}
          </button>
          {versionResult && (
            <>
              <div style={{ display: "flex", gap: 14, marginTop: 18, flexWrap: "wrap" }}>
                <VersionCard title={t("tax.expired")} rate={versionResult.expiredRate} color="var(--neon-orange)" />
                <VersionCard title={t("tax.created")} rate={versionResult.newRate} color="var(--neon-green)" />
              </div>
              <div style={{ marginTop: 18 }}>
                <DataTable
                  screenCode="TAX_RATE_VERSION_LIST"
                  rowKey={(r) => r.version}
                  rows={(
                    [
                      versionResult.expiredRate && {
                        version: versionResult.expiredRate.id,
                        rate: versionResult.expiredRate.rate,
                        effectiveFrom: versionResult.expiredRate.startDate,
                      },
                      versionResult.newRate && {
                        version: versionResult.newRate.id,
                        rate: versionResult.newRate.rate,
                        effectiveFrom: versionResult.newRate.startDate,
                      },
                    ].filter(Boolean) as TaxVersionRow[]
                  )}
                  fallbackColumns={TAX_RATE_VERSION_LIST_COLUMNS}
                  showColumnPicker={false}
                  renderers={{
                    version: (r) => r.version,
                    rate: (r) => formatNumber(r.rate, 2),
                    effectiveFrom: (r) => (r.effectiveFrom ? formatDate(r.effectiveFrom) : "—"),
                  }}
                />
              </div>
            </>
          )}
        </Section>

        <Section title={t("tax.simulator")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("tax.baseAmount")}>
              <input className="form-input" value={base} onChange={(e) => setBase(e.target.value)} />
            </Field>
            <Field label="Vergi Tipi">
              <input className="form-input" value={taxTypeCode} onChange={(e) => setTaxTypeCode(e.target.value)} />
            </Field>
            <Field label="Mod">
              <select className="form-select" value={mode} onChange={(e) => setMode(e.target.value as "EXCLUSIVE" | "INCLUSIVE")}>
                <option value="EXCLUSIVE">{t("tax.exclusive")}</option>
                <option value="INCLUSIVE">{t("tax.inclusive")}</option>
              </select>
            </Field>
          </div>
          <button className="btn btn-secondary" disabled={simBusy} onClick={() => void runSimulation()}>
            <Percent size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("tax.calculate")}
          </button>
          {sim && (
            <div className="table-container" style={{ marginTop: 12 }}>
              <table className="wms-table">
                <tbody>
                  <tr><td>{t("tax.net")}</td><td>{formatNumber(sim.net)}</td></tr>
                  <tr><td>{t("tax.taxAmount")}</td><td style={{ color: "var(--neon-orange)" }}>{formatNumber(sim.tax)}</td></tr>
                  <tr><td>{t("tax.gross")}</td><td style={{ fontWeight: 700 }}>{formatNumber(sim.gross)}</td></tr>
                  <tr><td>Oran</td><td>%{formatNumber(sim.rate, 2)}</td></tr>
                </tbody>
              </table>
            </div>
          )}
        </Section>
      </div>
    </div>
  );
}
