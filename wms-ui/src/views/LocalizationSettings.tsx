/**
 * İş İsterleri 6-7 — Saat Dilimi + Uluslararası Tarih/Saat Formatları
 * Aktif format konfigürasyonu localization-service'ten (GET /api/v1/formats/active),
 * saat dilimi seçimi kullanıcı bazlı; tüm gösterimler UTC → seçili TZ dönüşümüyle.
 */

import { useMemo, useState } from "react";
import { Clock, Globe2 } from "lucide-react";
import { DataTable } from "../components/DataTable";
import { useI18n } from "../i18n/I18nContext";
import { Field, InfoBanner, PageHeader, Section, grid2 } from "../components/common";
import { fallbackCol } from "../components/tableUtils";

const LOCALE_PREVIEW_FALLBACK = [
  fallbackCol("sample", "columns.locale.sample", 0),
  fallbackCol("raw", "columns.locale.raw", 1),
  fallbackCol("display", "columns.locale.display", 2),
];

interface LocalePreviewRow {
  key: string;
  sample: string;
  raw: string;
  display: string;
}

const COMMON_TIMEZONES = [
  "Europe/Istanbul", "Europe/Berlin", "Europe/London", "America/New_York",
  "America/Chicago", "America/Los_Angeles", "Asia/Dubai", "Asia/Riyadh",
  "Asia/Shanghai", "Asia/Tokyo", "UTC",
];

export function LocalizationSettings() {
  const { t, timezone, setTimezone, format, formatFromServer, formatDate, formatDateTime, formatNumber } = useI18n();
  const [sampleAmount] = useState(1234567.89);

  const allTimezones = useMemo<string[]>(() => {
    try {
      const sup = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.("timeZone");
      return sup && sup.length > 0 ? sup : COMMON_TIMEZONES;
    } catch {
      return COMMON_TIMEZONES;
    }
  }, []);

  const now = new Date();
  const previewRows: LocalePreviewRow[] = [
    { key: "date", sample: t("common.date"), raw: now.toISOString(), display: formatDate(now) },
    { key: "datetime", sample: `${t("common.date")} + saat`, raw: now.toISOString(), display: formatDateTime(now) },
    { key: "amount", sample: "Tutar", raw: String(sampleAmount), display: formatNumber(sampleAmount) },
  ];
  const utcOffset = useMemo(() => {
    try {
      const p = new Intl.DateTimeFormat("en-US", { timeZone: timezone, timeZoneName: "shortOffset" })
        .formatToParts(now)
        .find((x) => x.type === "timeZoneName");
      return p?.value ?? "";
    } catch {
      return "";
    }
  }, [timezone, now]);

  return (
    <div>
      <PageHeader title={t("locale.title")} subtitle={t("locale.subtitle")} />
      <InfoBanner message={t("locale.utcNote")} />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 20 }}>
        <Section title={t("locale.timezone")} style={{ marginBottom: 0 }}>
          <Field label={t("locale.timezone")}>
            <select className="form-select" value={timezone} onChange={(e) => setTimezone(e.target.value)}>
              {allTimezones.map((tz) => (
                <option key={tz} value={tz}>{tz}</option>
              ))}
            </select>
          </Field>
          <div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--text-secondary)", fontSize: "0.85rem" }}>
            <Clock size={15} color="var(--neon-blue)" />
            {timezone} <span className="badge badge-blue">{utcOffset}</span>
          </div>
        </Section>

        <Section title={t("locale.activeFormat")} style={{ marginBottom: 0 }}>
          <div style={grid2}>
            <Field label={t("locale.dateFormat")}>
              <input className="form-input" value={format.dateFormat} readOnly />
            </Field>
            <Field label={t("locale.timeFormat")}>
              <input className="form-input" value={format.timeFormat} readOnly />
            </Field>
            <Field label={t("locale.decimalSep")}>
              <input className="form-input" value={format.decimalSeparator} readOnly />
            </Field>
            <Field label={t("locale.thousandSep")}>
              <input className="form-input" value={format.thousandSeparator} readOnly />
            </Field>
          </div>
          <div style={{ fontSize: "0.78rem", color: formatFromServer ? "var(--neon-green)" : "var(--neon-orange)" }}>
            <Globe2 size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />
            {formatFromServer ? t("locale.serverSource") : t("locale.fallbackSource")}
          </div>
        </Section>
      </div>

      <Section title={t("locale.preview")} style={{ marginTop: 20 }}>
        <DataTable
          screenCode="LOCALE_PREVIEW_LIST"
          rowKey={(row) => row.key}
          rows={previewRows}
          showColumnPicker
          fallbackColumns={LOCALE_PREVIEW_FALLBACK}
          renderers={{
            sample: (row) => row.sample,
            raw: (row) => <span style={{ fontFamily: "monospace" }}>{row.raw}</span>,
            display: (row) => <span style={{ color: "var(--neon-green)", fontWeight: 600 }}>{row.display}</span>,
          }}
        />
      </Section>
    </div>
  );
}
