/**
 * Çoklu para birimi fatura — billing-service.
 */

import { useCallback, useEffect, useState } from "react";
import { Calculator, Plus, Save, Trash2, CheckCircle, XCircle, TrendingUp } from "lucide-react";
import type { AxiosError } from "axios";
import {
  billingService,
  describeError,
  type CreateInvoiceRequest,
  type InvoiceItemInput,
  type InvoiceResponse,
  type InvoiceStatus,
} from "../../api/services";
import { canAccessAny } from "../../auth/roles";
import { CURRENCIES } from "../../constants/currencies";
import { useI18n } from "../../i18n/I18nContext";
import { DataTable } from "../../components/DataTable";
import { Field, Loading, Section, grid2 } from "../../components/common";
import { fallbackCol } from "../../components/tableUtils";

const INVOICE_LIST_FALLBACK = [
  fallbackCol("number", "columns.invoice.number", 0),
  fallbackCol("customer", "columns.invoice.customer", 1),
  fallbackCol("amount", "columns.finance.amount", 2, { dataType: "NUMBER" }),
  fallbackCol("status", "columns.common.status", 3),
];

export interface InvoiceTabProps {
  activeLocationId?: number | "";
  tenantReady: boolean;
  run: (fn: () => Promise<string | void>, successMsg?: string) => Promise<void>;
}

interface LineFormRow {
  key: string;
  itemDescription: string;
  quantity: string;
  unitPriceOriginal: string;
  discountOriginal: string;
  taxTypeCode: string;
  taxRate: string;
}

const today = () => new Date().toISOString().slice(0, 10);

const emptyLine = (): LineFormRow => ({
  key: crypto.randomUUID(),
  itemDescription: "",
  quantity: "1",
  unitPriceOriginal: "",
  discountOriginal: "0",
  taxTypeCode: "KDV",
  taxRate: "",
});

const parseNum = (s: string) => parseFloat(s.replace(",", "."));

const statusBadge = (status: InvoiceStatus) => {
  switch (status) {
    case "DRAFT":
      return "badge-orange";
    case "APPROVED":
      return "badge-green";
    case "CANCELLED":
      return "badge-purple";
    case "SENT_TO_ERP":
      return "badge-blue";
    default:
      return "badge-green";
  }
};

export function InvoiceTab({ activeLocationId, tenantReady, run }: InvoiceTabProps) {
  const { t, formatNumber, formatDate, formatDateTime } = useI18n();
  const canApprove = canAccessAny(["FINANCE_MANAGER", "WMS_ADMIN"]);

  const [customerId, setCustomerId] = useState("");
  const [invoiceCurrency, setInvoiceCurrency] = useState("EUR");
  const [exchangeRateDate, setExchangeRateDate] = useState(today());
  const [lines, setLines] = useState<LineFormRow[]>([emptyLine()]);
  const [preview, setPreview] = useState<InvoiceResponse | null>(null);

  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState<InvoiceStatus | "">("");
  const [filterCustomer, setFilterCustomer] = useState("");

  const [exchInvoiceId, setExchInvoiceId] = useState<number | null>(null);
  const [exchPaid, setExchPaid] = useState("");
  const [exchRate, setExchRate] = useState("");

  const locationReady = tenantReady && Boolean(activeLocationId);

  const loadInvoices = useCallback(() => {
    if (!locationReady) return;
    setListLoading(true);
    billingService
      .listInvoices({
        status: filterStatus || undefined,
        customerId: filterCustomer.trim() ? Number(filterCustomer) : undefined,
        size: 20,
      })
      .then((page) => setInvoices(page.content))
      .catch(() => setInvoices([]))
      .finally(() => setListLoading(false));
  }, [locationReady, filterStatus, filterCustomer]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  const buildRequest = (): CreateInvoiceRequest | null => {
    const cid = Number(customerId);
    if (!cid || cid <= 0) return null;
    const items: InvoiceItemInput[] = [];
    for (const line of lines) {
      const qty = parseNum(line.quantity);
      const price = parseNum(line.unitPriceOriginal);
      const discount = parseNum(line.discountOriginal || "0");
      const taxOverride = line.taxRate.trim() ? parseNum(line.taxRate) : undefined;
      if (!line.itemDescription.trim() || !line.taxTypeCode.trim() || isNaN(qty) || qty <= 0 || isNaN(price) || price < 0) return null;
      if (isNaN(discount) || discount < 0) return null;
      if (taxOverride !== undefined && (isNaN(taxOverride) || taxOverride < 0 || taxOverride > 100)) return null;
      items.push({
        itemDescription: line.itemDescription.trim(),
        quantity: qty,
        unitPriceOriginal: price,
        discountOriginal: discount,
        taxTypeCode: line.taxTypeCode.trim(),
        taxRate: taxOverride,
      });
    }
    if (items.length === 0) return null;
    return { customerId: cid, invoiceCurrency, exchangeRateDate, items };
  };

  const formValid = buildRequest() !== null;

  const handleCalculate = () => {
    const req = buildRequest();
    if (!req) return;
    void run(async () => {
      const result = await billingService.calculateInvoice(req);
      setPreview(result);
    });
  };

  const handleSave = () => {
    const req = buildRequest();
    if (!req) return;
    void run(
      async () => {
        await billingService.createInvoice(req);
        setPreview(null);
        setCustomerId("");
        setLines([emptyLine()]);
        loadInvoices();
        return t("bill.invoice.saved");
      },
    );
  };

  const handleApprove = (id: number) =>
    void run(async () => {
      await billingService.approveInvoice(id);
      loadInvoices();
      return t("bill.invoice.approved");
    });

  const handleCancel = (id: number) =>
    void run(async () => {
      await billingService.cancelInvoice(id);
      loadInvoices();
      return t("bill.invoice.cancelled");
    });

  const handleExchangeDiff = (invoiceId: number) => {
    const paid = parseNum(exchPaid);
    const rate = parseNum(exchRate);
    if (isNaN(paid) || paid <= 0 || isNaN(rate) || rate <= 0) return;
    void run(async () => {
      await billingService.recordExchangeDifference(invoiceId, {
        paidAmountOriginal: paid,
        rateAtPayment: rate,
      });
      setExchInvoiceId(null);
      setExchPaid("");
      setExchRate("");
      loadInvoices();
      return t("bill.invoice.exchangeDiffSaved");
    });
  };

  const addLine = () => setLines((prev) => [...prev, emptyLine()]);
  const removeLine = (key: string) => setLines((prev) => (prev.length <= 1 ? prev : prev.filter((l) => l.key !== key)));
  const updateLine = (key: string, patch: Partial<LineFormRow>) =>
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  if (!locationReady) {
    return (
      <Section title={t("bill.invoice.title")}>
        <p style={{ color: "var(--text-muted)", margin: 0 }}>{t("bill.invoice.tenantRequired")}</p>
      </Section>
    );
  }

  return (
    <>
      <Section title={t("bill.invoice.new")}>
        <div style={grid2}>
          <Field label={t("bill.customerId")}>
            <input className="form-input" value={customerId} onChange={(e) => setCustomerId(e.target.value)} />
          </Field>
          <Field label={t("bill.invoice.currency")}>
            <select className="form-select" value={invoiceCurrency} onChange={(e) => setInvoiceCurrency(e.target.value)}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </Field>
          <Field label={t("bill.invoice.rateDate")}>
            <input type="date" className="form-input" value={exchangeRateDate} onChange={(e) => setExchangeRateDate(e.target.value)} />
          </Field>
        </div>

        <div style={{ marginTop: 16, marginBottom: 8, fontWeight: 600, fontSize: "0.85rem" }}>{t("bill.invoice.lines")}</div>
        {lines.map((line) => (
          <div key={line.key} style={{ ...grid2, marginBottom: 12, alignItems: "end" }}>
            <Field label={t("bill.invoice.lineDescription")}>
              <input
                className="form-input"
                value={line.itemDescription}
                onChange={(e) => updateLine(line.key, { itemDescription: e.target.value })}
              />
            </Field>
            <Field label={t("bill.invoice.quantity")}>
              <input className="form-input" value={line.quantity} onChange={(e) => updateLine(line.key, { quantity: e.target.value })} />
            </Field>
            <Field label={t("bill.invoice.unitPrice")}>
              <input
                className="form-input"
                placeholder="100,00"
                value={line.unitPriceOriginal}
                onChange={(e) => updateLine(line.key, { unitPriceOriginal: e.target.value })}
              />
            </Field>
            <Field label={t("bill.invoice.discount")}>
              <input
                className="form-input"
                value={line.discountOriginal}
                onChange={(e) => updateLine(line.key, { discountOriginal: e.target.value })}
              />
            </Field>
            <Field label={t("bill.invoice.taxType")}>
              <input className="form-input" value={line.taxTypeCode} onChange={(e) => updateLine(line.key, { taxTypeCode: e.target.value })} placeholder="KDV" />
            </Field>
            <Field label={t("bill.invoice.taxRateOverride")}>
              <input className="form-input" value={line.taxRate} onChange={(e) => updateLine(line.key, { taxRate: e.target.value })} placeholder={t("bill.invoice.taxRateAuto")} />
            </Field>
            <div>
              <button type="button" className="btn btn-secondary" disabled={lines.length <= 1} onClick={() => removeLine(line.key)} style={{ padding: "6px 10px" }}>
                <Trash2 size={14} />
              </button>
            </div>
          </div>
        ))}
        <button type="button" className="btn btn-secondary" onClick={addLine} style={{ marginBottom: 16 }}>
          <Plus size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
          {t("bill.invoice.addLine")}
        </button>

        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <button className="btn btn-secondary" disabled={!formValid} onClick={handleCalculate}>
            <Calculator size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("bill.invoice.calculate")}
          </button>
          <button className="btn btn-primary" disabled={!formValid} onClick={handleSave}>
            <Save size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("bill.invoice.save")}
          </button>
        </div>

        {preview && (
          <div
            style={{
              marginTop: 20,
              padding: 16,
              borderRadius: 8,
              border: "1px solid var(--border-subtle)",
              background: "var(--bg-elevated)",
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: 10 }}>{t("bill.invoice.preview")}</div>
            <div style={grid2}>
              <div>
                {t("bill.subtotal")}: {preview.invoiceCurrency} {formatNumber(preview.subtotalOriginal)}
              </div>
              <div>
                {t("bill.tax")}: {preview.invoiceCurrency} {formatNumber(preview.taxAmountOriginal)}
              </div>
              <div style={{ fontWeight: 600 }}>
                {t("bill.grandTotal")}: {preview.invoiceCurrency} {formatNumber(preview.grandTotalOriginal)}
              </div>
              <div>
                {t("bill.exchangeRate")}: {formatNumber(preview.exchangeRateValue, 4)} ({formatDate(preview.exchangeRateDate)})
              </div>
              <div style={{ color: "var(--neon-green)", fontWeight: 600 }}>
                {t("bill.convertedAmount")}: {preview.accountingCurrency} {formatNumber(preview.grandTotalAccounting)}
              </div>
            </div>
          </div>
        )}
      </Section>

      <Section title={t("bill.invoice.list")}>
        <div style={{ ...grid2, marginBottom: 16 }}>
          <Field label={t("bill.invoice.filterStatus")}>
            <select className="form-select" value={filterStatus} onChange={(e) => setFilterStatus(e.target.value as InvoiceStatus | "")}>
              <option value="">{t("common.all")}</option>
              {(["DRAFT", "APPROVED", "CANCELLED", "SENT_TO_ERP"] as InvoiceStatus[]).map((s) => (
                <option key={s} value={s}>{t(`bill.invoice.status.${s}`)}</option>
              ))}
            </select>
          </Field>
          <Field label={t("bill.customerId")}>
            <input className="form-input" value={filterCustomer} onChange={(e) => setFilterCustomer(e.target.value)} placeholder={t("common.optional")} />
          </Field>
        </div>

        {listLoading ? (
          <Loading />
        ) : (
          <DataTable
            screenCode="INVOICE_LIST"
            rowKey={(inv) => String(inv.id ?? inv.invoiceNumber)}
            rows={invoices}
            loading={listLoading}
            emptyMessage={t("bill.invoice.empty")}
            fallbackColumns={INVOICE_LIST_FALLBACK}
            renderers={{
              number: (inv) => (
                <>
                  <span style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{inv.invoiceNumber}</span>
                  <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)" }}>
                    {inv.issueDate ? formatDateTime(inv.issueDate) : "—"}
                  </span>
                </>
              ),
              customer: (inv) => (
                <>
                  {inv.customerId}
                  <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)" }}>{inv.invoiceCurrency}</span>
                </>
              ),
              amount: (inv) => (
                <>
                  <div style={{ fontWeight: 600 }}>
                    {inv.invoiceCurrency} {formatNumber(inv.grandTotalOriginal)}
                  </div>
                  <div style={{ color: "var(--neon-green)", fontSize: "0.85rem" }}>
                    {inv.accountingCurrency} {formatNumber(inv.grandTotalAccounting)}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
                    {t("bill.exchangeRate")}: {formatNumber(inv.exchangeRateValue, 4)}
                  </div>
                </>
              ),
              status: (inv) => (
                <>
                  <span className={`badge ${statusBadge(inv.status)}`}>{t(`bill.invoice.status.${inv.status}`)}</span>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }}>
                    {inv.status === "DRAFT" && canApprove && (
                      <>
                        <button type="button" className="btn btn-success" style={{ padding: "4px 10px" }} onClick={() => handleApprove(inv.id!)}>
                          <CheckCircle size={12} />
                        </button>
                        <button type="button" className="btn btn-danger" style={{ padding: "4px 10px" }} onClick={() => handleCancel(inv.id!)}>
                          <XCircle size={12} />
                        </button>
                      </>
                    )}
                    {inv.status === "APPROVED" && (
                      <button
                        type="button"
                        className="btn btn-secondary"
                        style={{ padding: "4px 10px" }}
                        onClick={() => {
                          setExchInvoiceId(exchInvoiceId === inv.id ? null : inv.id!);
                          setExchPaid(String(inv.grandTotalOriginal));
                          setExchRate(String(inv.exchangeRateValue));
                        }}
                      >
                        <TrendingUp size={12} />
                      </button>
                    )}
                  </div>
                  {exchInvoiceId === inv.id && (
                    <div style={{ marginTop: 8, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "end" }}>
                      <Field label={t("bill.invoice.paidAmount")}>
                        <input className="form-input" value={exchPaid} onChange={(e) => setExchPaid(e.target.value)} style={{ width: 100 }} />
                      </Field>
                      <Field label={t("bill.invoice.paymentRate")}>
                        <input className="form-input" value={exchRate} onChange={(e) => setExchRate(e.target.value)} style={{ width: 100 }} />
                      </Field>
                      <button type="button" className="btn btn-primary" style={{ padding: "4px 10px" }} onClick={() => handleExchangeDiff(inv.id!)}>
                        {t("bill.invoice.exchangeDiff")}
                      </button>
                    </div>
                  )}
                </>
              ),
            }}
          />
        )}
      </Section>
    </>
  );
}

/** Rate-lock ve exchange-rate hatalarını üst bileşene iletmek için yardımcı. */
export function mapBillingError(e: unknown, t: (key: string) => string): string {
  const err = e as AxiosError<Record<string, unknown>>;
  const status = err?.response?.status;
  if (status === 409) return `${t("bill.invoice.rateLocked")} · ${describeError(e)}`;
  if (status === 404 && /exchange|kur/i.test(describeError(e))) return `${t("bill.invoice.rateNotFound")} · ${describeError(e)}`;
  return describeError(e);
}
