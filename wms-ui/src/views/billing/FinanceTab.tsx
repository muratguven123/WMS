/**
 * Sipariş, sözleşme ve finansal işlem — finance-service (İş İsterleri 8-9).
 */

import { useState } from "react";
import { FileText, Receipt, ScrollText } from "lucide-react";
import {
  financeService,
  type ContractDto,
  type FinancialTransactionDto,
  type OrderResponse,
} from "../../api/services";
import { useI18n } from "../../i18n/I18nContext";
import { DataTable } from "../../components/DataTable";
import { Field, Section, grid2 } from "../../components/common";
import { fallbackCol } from "../../components/tableUtils";

const FINANCE_TRANSACTION_FALLBACK = [
  fallbackCol("id", "columns.common.id", 0, { dataType: "NUMBER" }),
  fallbackCol("type", "columns.finance.type", 1),
  fallbackCol("amount", "columns.finance.amount", 2, { dataType: "NUMBER" }),
  fallbackCol("date", "columns.common.date", 3, { dataType: "DATE" }),
];

const FINANCE_BALANCE_FALLBACK = [
  fallbackCol("account", "columns.finance.account", 0),
  fallbackCol("balance", "columns.finance.balance", 1, { dataType: "NUMBER" }),
  fallbackCol("currency", "columns.finance.currency", 2),
];

export interface FinanceTabProps {
  run: (fn: () => Promise<string | void>, successMsg?: string) => Promise<void>;
}

export function FinanceTab({ run }: FinanceTabProps) {
  const { t, formatNumber, formatDateTime } = useI18n();

  const [oCustomer, setOCustomer] = useState("");
  const [oCurrency, setOCurrency] = useState("");
  const [oAmount, setOAmount] = useState("");
  const [oContract, setOContract] = useState("");
  const [orders, setOrders] = useState<OrderResponse[]>([]);

  const [cCustomer, setCCustomer] = useState("");
  const [cCurrency, setCCurrency] = useState("");
  const [cCode, setCCode] = useState("");
  const [_contracts, setContracts] = useState<ContractDto[]>([]);

  const [txCompany, setTxCompany] = useState("");
  const [txLocation, setTxLocation] = useState("");
  const [txCustomer, setTxCustomer] = useState("");
  const [txContract, setTxContract] = useState("");
  const [txAmount, setTxAmount] = useState("");
  const [txRateType, setTxRateType] = useState("SELLING");
  const [txns, setTxns] = useState<FinancialTransactionDto[]>([]);

  const createOrder = () =>
    run(async () => {
      const created = await financeService.createOrder({
        customerId: Number(oCustomer),
        currencyId: Number(oCurrency),
        amount: parseFloat(oAmount.replace(",", ".")),
        contractId: oContract.trim() ? Number(oContract) : null,
      });
      setOrders((o) => [created, ...o]);
      return `${t("bill.orderCreated")} — ${created.currencyCode} ${formatNumber(created.amount)}`;
    });

  const createContract = () =>
    run(async () => {
      const created = await financeService.createContract({
        customerId: Number(cCustomer),
        currencyId: Number(cCurrency),
        contractCode: cCode.trim(),
      });
      setContracts((c) => [created, ...c]);
      return `${t("bill.newContract")}: ${created.contractCode} (${created.currencyCode})`;
    });

  const recordTxn = () =>
    run(async () => {
      const created = await financeService.recordTransaction({
        companyId: Number(txCompany),
        locationId: Number(txLocation),
        customerId: txCustomer.trim() ? Number(txCustomer) : null,
        contractId: txContract.trim() ? Number(txContract) : null,
        amount: parseFloat(txAmount.replace(",", ".")),
        transactionDate: new Date().toISOString(),
        rateType: txRateType,
      });
      setTxns((x) => [created, ...x]);
      return t("bill.txnResult");
    });

  return (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(360px, 1fr))", gap: 20, marginBottom: 20 }}>
        <Section title={t("bill.newOrder")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("bill.customerId")}>
              <input className="form-input" value={oCustomer} onChange={(e) => setOCustomer(e.target.value)} />
            </Field>
            <Field label={t("bill.currencyId")}>
              <input className="form-input" value={oCurrency} onChange={(e) => setOCurrency(e.target.value)} />
            </Field>
            <Field label={t("bill.amount")}>
              <input className="form-input" placeholder="1500,00" value={oAmount} onChange={(e) => setOAmount(e.target.value)} />
            </Field>
            <Field label={t("bill.contractId")}>
              <input className="form-input" value={oContract} onChange={(e) => setOContract(e.target.value)} />
            </Field>
          </div>
          <button className="btn btn-primary" disabled={!oCustomer || !oCurrency || !oAmount} onClick={() => void createOrder()}>
            <Receipt size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("common.add")}
          </button>
        </Section>

        <Section title={t("bill.newContract")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("bill.customerId")}>
              <input className="form-input" value={cCustomer} onChange={(e) => setCCustomer(e.target.value)} />
            </Field>
            <Field label={t("bill.currencyId")}>
              <input className="form-input" value={cCurrency} onChange={(e) => setCCurrency(e.target.value)} />
            </Field>
            <Field label={t("bill.contractCode")}>
              <input className="form-input" placeholder="CNTR-2026-001" value={cCode} onChange={(e) => setCCode(e.target.value)} />
            </Field>
          </div>
          <button className="btn btn-primary" disabled={!cCustomer || !cCurrency || !cCode} onClick={() => void createContract()}>
            <ScrollText size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("common.add")}
          </button>
        </Section>

        <Section title={t("bill.newTxn")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label="Company ID">
              <input className="form-input" value={txCompany} onChange={(e) => setTxCompany(e.target.value)} />
            </Field>
            <Field label="Location ID">
              <input className="form-input" value={txLocation} onChange={(e) => setTxLocation(e.target.value)} />
            </Field>
            <Field label={t("bill.customerId")}>
              <input className="form-input" value={txCustomer} onChange={(e) => setTxCustomer(e.target.value)} />
            </Field>
            <Field label={t("bill.contractId")}>
              <input className="form-input" value={txContract} onChange={(e) => setTxContract(e.target.value)} />
            </Field>
            <Field label={t("bill.amount")}>
              <input className="form-input" placeholder="2500,00" value={txAmount} onChange={(e) => setTxAmount(e.target.value)} />
            </Field>
            <Field label={t("fx.rateType")}>
              <select className="form-select" value={txRateType} onChange={(e) => setTxRateType(e.target.value)}>
                {["BUYING", "SELLING", "EFFECTIVE_BUYING", "EFFECTIVE_SELLING"].map((rt) => (
                  <option key={rt} value={rt}>{rt}</option>
                ))}
              </select>
            </Field>
          </div>
          <button className="btn btn-success" disabled={!txCompany || !txLocation || !txAmount} onClick={() => void recordTxn()}>
            <FileText size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("common.save")}
          </button>
        </Section>
      </div>

      {txns.length > 0 && (
        <Section title={t("bill.txnResult")}>
          <DataTable
            screenCode="FINANCE_TRANSACTION_LIST"
            rowKey={(x) => x.id}
            rows={txns}
            fallbackColumns={FINANCE_TRANSACTION_FALLBACK}
            renderers={{
              id: (x) => x.id,
              type: (x) => (
                <>
                  {x.originalCurrencyCode} → {x.baseCurrencyCode}
                  {x.fallbackRateUsed ? (
                    <span className="badge badge-orange" style={{ marginLeft: 6, fontSize: "0.65rem" }}>FALLBACK</span>
                  ) : (
                    <span className="badge badge-green" style={{ marginLeft: 6, fontSize: "0.65rem" }}>EXACT</span>
                  )}
                </>
              ),
              amount: (x) => (
                <>
                  <div style={{ fontWeight: 600 }}>
                    {x.originalCurrencyCode} {formatNumber(x.originalAmount)}
                  </div>
                  <div style={{ color: "var(--neon-green)", fontWeight: 600 }}>
                    {x.baseCurrencyCode} {formatNumber(x.convertedAmount)}
                  </div>
                  <div style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                    {t("bill.exchangeRate")}: {formatNumber(x.exchangeRate, 4)}
                  </div>
                </>
              ),
              date: () => "—",
            }}
          />
        </Section>
      )}

      {orders.length > 0 && (
        <Section title={t("bill.newOrder")}>
          <DataTable
            screenCode="FINANCE_BALANCE_LIST"
            rowKey={(o) => o.orderId}
            rows={orders}
            fallbackColumns={FINANCE_BALANCE_FALLBACK}
            renderers={{
              account: (o) => <span style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{o.orderId}</span>,
              balance: (o) => <span style={{ fontWeight: 600 }}>{formatNumber(o.amount)}</span>,
              currency: (o) => (
                <>
                  {o.currencyCode}
                  <span style={{ display: "block", fontSize: "0.78rem", color: "var(--text-muted)" }}>
                    {formatDateTime(o.orderDate)}
                  </span>
                </>
              ),
            }}
          />
        </Section>
      )}
    </>
  );
}
