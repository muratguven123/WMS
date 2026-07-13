/**
 * İş İsteri 8 — Çoklu Para Birimi ve Döviz Kuru Yönetimi
 * Kur sorgulama (GET /api/rates/lookup — işlem tarihindeki kur + fallback bilgisi),
 * aktif kur listesi (GET /api/rates/active), TCMB yenileme (POST /api/rates/sync/tcmb),
 * manuel kur girişi (POST /api/rates/manual — audit log + cache eviction backend'de).
 */

import { useCallback, useEffect, useState } from "react";
import { ArrowRightLeft, RefreshCw, TrendingUp } from "lucide-react";
import {
  describeError,
  financeService,
  type ActiveRateListDto,
  type ExchangeRateDto,
  type ManualRateResponse,
  type RateType,
} from "../api/services";
import { CURRENCIES } from "../constants/currencies";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";

const RATE_TYPES: RateType[] = ["BUYING", "SELLING", "EFFECTIVE_BUYING", "EFFECTIVE_SELLING"];
const today = () => new Date().toISOString().slice(0, 10);

const CURRENCY_RATE_LIST_COLUMNS = [
  fallbackCol("pair", "columns.fx.pair", 0),
  fallbackCol("rate", "columns.fx.rate", 1, { dataType: "NUMBER" }),
  fallbackCol("updatedAt", "columns.common.updatedAt", 2, { dataType: "DATE" }),
];

const CURRENCY_RATE_HISTORY_LIST_COLUMNS = [
  fallbackCol("date", "columns.common.date", 0, { dataType: "DATE" }),
  fallbackCol("rate", "columns.fx.rate", 1, { dataType: "NUMBER" }),
  fallbackCol("source", "columns.fx.source", 2),
];

const sourceBadge = (s: string) =>
  s?.includes("TCMB") ? "badge-blue" : s?.includes("MANUAL") ? "badge-orange" : s?.includes("ERP") ? "badge-purple" : "badge-green";

export function CurrencyExchange() {
  const { t, formatNumber, formatDate, formatDateTime } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Aktif kurlar
  const [activeRates, setActiveRates] = useState<ActiveRateListDto | null>(null);
  const [activeLoading, setActiveLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);

  // Lookup
  const [from, setFrom] = useState("USD");
  const [to, setTo] = useState("TRY");
  const [date, setDate] = useState(today());
  const [type, setType] = useState<RateType>("SELLING");
  const [result, setResult] = useState<ExchangeRateDto | null>(null);
  const [history, setHistory] = useState<ExchangeRateDto[]>([]);

  // Manuel giriş
  const [mFrom, setMFrom] = useState("USD");
  const [mTo, setMTo] = useState("TRY");
  const [mDate, setMDate] = useState(today());
  const [mType, setMType] = useState<RateType>("SELLING");
  const [mRate, setMRate] = useState("");
  const [manualResult, setManualResult] = useState<ManualRateResponse | null>(null);

  const loadActiveRates = useCallback(async () => {
    setActiveLoading(true);
    try {
      const data = await financeService.listActiveRates({ rateType: type, rateDate: date });
      setActiveRates(data);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setActiveLoading(false);
    }
  }, [type, date]);

  useEffect(() => {
    void loadActiveRates();
  }, [loadActiveRates]);

  const refreshTcmb = async () => {
    setError(null);
    setSuccess(null);
    setSyncing(true);
    try {
      const sync = await financeService.syncTcmbRates();
      if (!sync.success) {
        setError(t("fx.syncFailed"));
        return;
      }
      setSuccess(t("fx.syncSuccess"));
      await loadActiveRates();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setSyncing(false);
    }
  };

  const lookup = async () => {
    setError(null);
    setResult(null);
    try {
      const r = await financeService.lookupRate(from, to, date, type);
      setResult(r);
      setHistory((h) => [r, ...h].slice(0, 8));
    } catch (e) {
      setError(describeError(e));
    }
  };

  const saveManual = async () => {
    setError(null);
    setSuccess(null);
    setManualResult(null);
    const rate = parseFloat(mRate.replace(",", "."));
    if (isNaN(rate) || rate <= 0) {
      setError(t("fx.invalidRate"));
      return;
    }
    try {
      const r = await financeService.upsertManualRate({
        sourceCurrency: mFrom, targetCurrency: mTo, rateDate: mDate, rateType: mType, rate,
      });
      setManualResult(r);
      setSuccess(`${t("fx.saved")} (${r.action ?? "OK"})`);
      await loadActiveRates();
    } catch (e) {
      setError(describeError(e));
    }
  };

  const CurrencySelect = ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <select className="form-select" value={value} onChange={(e) => onChange(e.target.value)}>
      {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
    </select>
  );

  return (
    <div>
      <PageHeader title={t("fx.title")} subtitle={t("fx.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />
      <InfoBanner message={t("fx.infoBanner")} />

      <Section title={t("fx.activeRates")}>
        <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap", marginBottom: 16 }}>
          <Field label={t("fx.rateType")}>
            <select className="form-select" value={type} onChange={(e) => setType(e.target.value as RateType)}>
              {RATE_TYPES.map((rt) => <option key={rt} value={rt}>{rt}</option>)}
            </select>
          </Field>
          <Field label={t("common.date")}>
            <input className="form-input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          <button className="btn btn-secondary" disabled={syncing} onClick={() => void refreshTcmb()} style={{ alignSelf: "flex-end" }}>
            <RefreshCw size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {syncing ? t("common.loading") : t("fx.refreshRates")}
          </button>
        </div>

        {activeRates?.lastTcmbSyncAt && (
          <div style={{ fontSize: "0.82rem", color: "var(--text-secondary)", marginBottom: 12 }}>
            {t("fx.lastSync")}: {formatDateTime(activeRates.lastTcmbSyncAt)}
          </div>
        )}

        <DataTable
          screenCode="CURRENCY_RATE_LIST"
          rowKey={(r) => `${r.sourceCurrency}-${r.rateType}`}
          rows={activeRates?.rates ?? []}
          loading={activeLoading}
          fallbackColumns={CURRENCY_RATE_LIST_COLUMNS}
          renderers={{
            pair: (r) => `${r.sourceCurrency}/${r.targetCurrency}`,
            rate: (r) => <span style={{ fontWeight: 600 }}>{formatNumber(r.rate, 4)}</span>,
            updatedAt: (r) => (
              <>
                {formatDate(r.rateDate)}
                {r.fallbackUsed && (
                  <span style={{ marginLeft: 6, color: "var(--neon-orange)", fontSize: "0.75rem" }}>⚠</span>
                )}
              </>
            ),
          }}
        />
      </Section>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(380px, 1fr))", gap: 20 }}>
        <Section title={t("fx.lookup")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("fx.source")}><CurrencySelect value={from} onChange={setFrom} /></Field>
            <Field label={t("fx.target")}><CurrencySelect value={to} onChange={setTo} /></Field>
            <Field label={t("common.date")}>
              <input className="form-input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </Field>
            <Field label={t("fx.rateType")}>
              <select className="form-select" value={type} onChange={(e) => setType(e.target.value as RateType)}>
                {RATE_TYPES.map((rt) => <option key={rt} value={rt}>{rt}</option>)}
              </select>
            </Field>
          </div>
          <button className="btn btn-primary" onClick={() => void lookup()}>
            <ArrowRightLeft size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("common.search")}
          </button>

          {result && (
            <div className="glass-card" style={{ marginTop: 18, padding: 18, border: "1px solid rgba(0,210,255,0.3)" }}>
              <div style={{ fontSize: "1.7rem", fontWeight: 700, color: "var(--neon-blue)", marginBottom: 6 }}>
                1 {result.sourceCurrency} = {formatNumber(result.rate, 4)} {result.targetCurrency}
              </div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", fontSize: "0.82rem", color: "var(--text-secondary)" }}>
                <span className={`badge ${sourceBadge(result.rateSource)}`}>{result.rateSource}</span>
                <span className="badge badge-blue">{result.rateType}</span>
                <span>{formatDate(result.rateDate)}</span>
              </div>
              {result.fallbackUsed && (
                <div style={{ marginTop: 10, color: "var(--neon-orange)", fontSize: "0.82rem" }}>⚠ {t("fx.fallback")}</div>
              )}
            </div>
          )}

          {history.length > 0 && (
            <div style={{ marginTop: 18 }}>
              <DataTable
                screenCode="CURRENCY_RATE_HISTORY_LIST"
                rowKey={(h) => `${h.sourceCurrency}-${h.targetCurrency}-${h.rateDate}-${h.rateType}`}
                rows={history}
                fallbackColumns={CURRENCY_RATE_HISTORY_LIST_COLUMNS}
                showColumnPicker={false}
                renderers={{
                  date: (h) => formatDate(h.rateDate),
                  rate: (h) => <span style={{ fontWeight: 600 }}>{formatNumber(h.rate, 4)}</span>,
                  source: (h) => <span className={`badge ${sourceBadge(h.rateSource)}`}>{h.rateSource}</span>,
                }}
              />
            </div>
          )}
        </Section>

        <Section title={t("fx.manualEntry")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("fx.source")}><CurrencySelect value={mFrom} onChange={setMFrom} /></Field>
            <Field label={t("fx.target")}><CurrencySelect value={mTo} onChange={setMTo} /></Field>
            <Field label={t("common.date")}>
              <input className="form-input" type="date" value={mDate} onChange={(e) => setMDate(e.target.value)} />
            </Field>
            <Field label={t("fx.rateType")}>
              <select className="form-select" value={mType} onChange={(e) => setMType(e.target.value as RateType)}>
                {RATE_TYPES.map((rt) => <option key={rt} value={rt}>{rt}</option>)}
              </select>
            </Field>
            <Field label={t("fx.rate")}>
              <input className="form-input" placeholder="34,2567" value={mRate} onChange={(e) => setMRate(e.target.value)} />
            </Field>
          </div>
          <button className="btn btn-success" disabled={!mRate} onClick={() => void saveManual()}>
            <TrendingUp size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("common.save")}
          </button>

          {manualResult && (
            <div className="glass-card" style={{ marginTop: 18, padding: 18, border: "1px solid rgba(0,245,155,0.3)" }}>
              <div style={{ color: "var(--text-primary)", fontWeight: 600, marginBottom: 6 }}>
                {manualResult.sourceCurrency}/{manualResult.targetCurrency} → {formatNumber(manualResult.rate, 4)}
                <span className="badge badge-green" style={{ marginLeft: 10 }}>{manualResult.action ?? "SAVED"}</span>
              </div>
              {manualResult.previousRate != null && (
                <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)" }}>
                  {t("fx.previousRate")}: {formatNumber(manualResult.previousRate, 4)}
                </div>
              )}
            </div>
          )}
        </Section>
      </div>
    </div>
  );
}
