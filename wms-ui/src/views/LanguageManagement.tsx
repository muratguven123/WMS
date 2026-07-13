/**
 * İş İsteri 2 — Çok Dilli Arayüz: Dil Yönetimi
 * Dilleri listeler (GET /api/v1/languages), kodsuz yeni dil ekler (POST),
 * çeviri dosyası import/export eder (xlsx/json).
 */

import { useEffect, useState } from "react";
import { Globe, Upload, Download, Plus } from "lucide-react";
import { describeError, localizationService, type Language } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { isLangActive, isLangDefault } from "../i18n/languageUtils";
import { DataTable } from "../components/DataTable";
import { ErrorBanner, Field, InfoBanner, PageHeader, Section, SuccessBanner, grid2 } from "../components/common";
import { fallbackCol } from "../components/tableUtils";

type TranslateProgress = { code: string; count: number; expected: number };

const POLL_DELAYS_MS = [500, 1000, 1000, 2000, 2000, 2000, 3000, 3000, 3000, 5000, 5000, 5000];

const LANGUAGE_LIST_FALLBACK = [
  fallbackCol("code", "columns.lang.code", 0),
  fallbackCol("name", "columns.lang.name", 1),
  fallbackCol("status", "columns.common.status", 2),
  fallbackCol("actions", "columns.common.actions", 99, { locked: true }),
];

const IMPORT_RESULT_FALLBACK = [
  fallbackCol("row", "columns.import.row", 0, { dataType: "NUMBER" }),
  fallbackCol("message", "columns.import.message", 1),
];

function interpolate(template: string, vars: Record<string, string | number>): string {
  return Object.entries(vars).reduce(
    (text, [key, value]) => text.replaceAll(`{${key}}`, String(value)),
    template,
  );
}

export function LanguageManagement() {
  const { t, lang: currentLang, formatDateTime, reloadLanguages, reloadTranslations, setLang } = useI18n();
  const [languages, setLanguages] = useState<Language[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [newCode, setNewCode] = useState("");
  const [newName, setNewName] = useState("");
  const [importLang, setImportLang] = useState("");
  const [busy, setBusy] = useState(false);
  const [retranslateSource, setRetranslateSource] = useState("");
  const [translateProgress, setTranslateProgress] = useState<TranslateProgress | null>(null);
  const [missing, setMissing] = useState<{ items: { keyCode: string; locale: string; hitCount: number }[]; unresolvedCount: number } | null>(null);

  const loadMissing = () => {
    localizationService
      .getMissingTranslations(undefined, 0, 25)
      .then((r) => setMissing({ items: r.items as { keyCode: string; locale: string; hitCount: number }[], unresolvedCount: r.unresolvedCount }))
      .catch(() => setMissing(null));
  };

  const load = () => {
    setError(null);
    localizationService
      .getLanguages(true)
      .then(setLanguages)
      .catch((e) => {
        setError(describeError(e));
        setLanguages([]);
      });
  };

  useEffect(() => {
    load();
    loadMissing();
  }, []);

  const waitForTranslations = async (
    code: string,
    onProgress?: (count: number, expected: number) => void,
  ) => {
    let langApplied = false;

    for (const delay of POLL_DELAYS_MS) {
      await new Promise((r) => setTimeout(r, delay));
      try {
        const status = await localizationService.getTranslationStatus(code);
        onProgress?.(status.uiKeyCount, status.expectedKeys);
        setTranslateProgress({
          code,
          count: status.uiKeyCount,
          expected: status.expectedKeys,
        });

        await reloadTranslations(code);
        await reloadLanguages();

        if (!langApplied && status.uiKeyCount > 0 && status.expectedKeys > 0) {
          setLang(code);
          langApplied = true;
        }

        if (status.ready) {
          await reloadTranslations(code);
          return status;
        }
      } catch {
        /* servis henüz hazır olmayabilir */
      }
    }
    return null;
  };

  const handleAdd = async () => {
    const code = newCode.trim().toLowerCase();
    const name = newName.trim();
    if (!code || !name) return;
    if (!/^[a-z]{2}$/.test(code)) {
      setError(t("lang.codeHint"));
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess(null);
    setTranslateProgress(null);
    try {
      const created = await localizationService.addLanguage(code, name);
      setSuccess(interpolate(t("lang.addedAutoTranslate"), { name: created.name, code: created.code }));
      setNewCode("");
      setNewName("");
      load();
      void reloadLanguages();
      void (async () => {
        const status = await waitForTranslations(created.code, (count, expected) => {
          setSuccess(interpolate(t("lang.translateProgress"), { count, expected }));
        });
        if (status) {
          setSuccess(interpolate(t("lang.translateComplete"), { count: status.uiKeyCount }));
          setLang(created.code);
        }
        setTranslateProgress(null);
        await reloadTranslations(created.code);
        await reloadLanguages();
      })();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleImport = async (file: File) => {
    if (!importLang.trim()) {
      setError(t("lang.importNeedCode"));
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await localizationService.importTranslations(importLang.trim().toLowerCase(), file);
      setSuccess(`${t("lang.importDone")}: ${JSON.stringify(result)}`);
      await reloadTranslations();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleRetranslate = async (code: string) => {
    setBusy(true);
    setError(null);
    setSuccess(null);
    setTranslateProgress(null);
    const source = retranslateSource.trim() || undefined;
    try {
      await localizationService.autoTranslate(code, true, source);
      setSuccess(t("lang.retranslateStarted"));
      const status = await waitForTranslations(code, (count, expected) => {
        setSuccess(interpolate(t("lang.retranslateProgress"), { count, expected }));
      });
      if (status) {
        setSuccess(interpolate(t("lang.retranslateDone"), { count: status.uiKeyCount }));
        setLang(code);
        await reloadTranslations(code);
      } else {
        setSuccess(t("lang.retranslateStarted"));
      }
      await reloadLanguages();
      setTranslateProgress(null);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleDeactivate = async (code: string) => {
    if (!window.confirm(interpolate(t("lang.deactivateConfirm"), { code }))) return;
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      await localizationService.deactivateLanguage(code);
      setSuccess(interpolate(t("lang.deactivated"), { code }));
      load();
      await reloadLanguages();
      if (currentLang === code) {
        const fallback = languages?.find((l) => isLangDefault(l))?.code ?? "tr";
        setLang(fallback);
        await reloadTranslations(fallback);
      }
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleExport = async (lang: string, format: "xlsx" | "json") => {
    setError(null);
    try {
      const blob = await localizationService.exportTranslations(lang, format);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `translations_${lang}_${new Date().toISOString().slice(0, 10)}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(describeError(e));
    }
  };

  const progressPct = translateProgress && translateProgress.expected > 0
    ? Math.min(100, Math.round((translateProgress.count / translateProgress.expected) * 100))
    : 0;

  return (
    <div>
      <PageHeader title={t("lang.title")} subtitle={t("lang.subtitle")} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      {translateProgress && (
        <div className="glass-card" style={{ padding: 16, marginBottom: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8, fontSize: "0.85rem" }}>
            <span style={{ color: "var(--text-secondary)" }}>
              {t("lang.retranslate")}: <strong>{translateProgress.code.toUpperCase()}</strong>
            </span>
            <span style={{ color: "var(--neon-blue)" }}>
              {translateProgress.count} / {translateProgress.expected} ({progressPct}%)
            </span>
          </div>
          <div className="capacity-bar-container" style={{ height: 8 }}>
            <div
              className="capacity-bar green"
              style={{ width: `${progressPct}%`, transition: "width 0.4s ease" }}
            />
          </div>
        </div>
      )}

      <Section title={t("lang.retranslate")} style={{ marginBottom: 20 }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "flex-end" }}>
          <Field label={t("lang.sourceLanguage")}>
            <select
              className="form-input"
              value={retranslateSource}
              onChange={(e) => setRetranslateSource(e.target.value)}
              style={{ minWidth: 200 }}
            >
              <option value="">{t("lang.sourceAuto")}</option>
              {(languages ?? []).map((l) => (
                <option key={l.code} value={l.code}>
                  {l.name} ({l.code})
                </option>
              ))}
            </select>
          </Field>
          <InfoBanner message={t("lang.sourceAuto")} />
        </div>
      </Section>

      <Section title={t("lang.title")}>
        <DataTable
          screenCode="LANGUAGE_LIST"
          rowKey={(l) => l.id}
          rows={languages ?? []}
          loading={languages === null}
          fallbackColumns={LANGUAGE_LIST_FALLBACK}
          renderers={{
            code: (l) => <span style={{ fontFamily: "monospace" }}>{l.code}</span>,
            name: (l) => (
              <>
                <Globe size={14} style={{ marginRight: 6, verticalAlign: "-2px" }} color="var(--neon-blue)" />
                {l.name}
                {isLangDefault(l) && (
                  <span className="badge badge-blue" style={{ marginLeft: 8, fontSize: "0.68rem" }}>
                    {t("lang.default")}
                  </span>
                )}
              </>
            ),
            status: (l) => (
              <span className={`badge ${isLangActive(l) ? "badge-green" : "badge-red"}`}>
                {isLangActive(l) ? t("common.active") : t("common.passive")}
              </span>
            ),
            actions: (l) => (
              <>
                {!isLangDefault(l) && isLangActive(l) && (
                  <button
                    className="btn btn-secondary"
                    style={{ padding: "4px 10px", marginRight: 6, color: "var(--neon-red, #f87171)" }}
                    disabled={busy}
                    onClick={() => void handleDeactivate(l.code)}
                  >
                    {t("lang.deactivate")}
                  </button>
                )}
                {!isLangDefault(l) && (
                  <button
                    className="btn btn-secondary"
                    style={{ padding: "4px 10px", marginRight: 6 }}
                    disabled={busy}
                    onClick={() => void handleRetranslate(l.code)}
                  >
                    {t("lang.retranslate")}
                  </button>
                )}
                <button className="btn btn-secondary" style={{ padding: "4px 10px", marginRight: 6 }} onClick={() => handleExport(l.code, "xlsx")}>
                  <Download size={13} style={{ verticalAlign: "-2px" }} /> xlsx
                </button>
                <button className="btn btn-secondary" style={{ padding: "4px 10px" }} onClick={() => handleExport(l.code, "json")}>
                  <Download size={13} style={{ verticalAlign: "-2px" }} /> json
                </button>
                <span style={{ display: "block", marginTop: 4, fontSize: "0.75rem", color: "var(--text-muted)" }}>
                  {formatDateTime(l.updatedAt)}
                </span>
              </>
            ),
          }}
        />
      </Section>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 20 }}>
        <Section title={t("lang.addNew")} style={{ marginBottom: 0 }}>
          <InfoBanner message={t("lang.codeHint")} />
          <div style={grid2}>
            <Field label={t("lang.code")}>
              <input
                className="form-input"
                placeholder="ru"
                maxLength={2}
                value={newCode}
                onChange={(e) => setNewCode(e.target.value.toLowerCase().replace(/[^a-z]/g, ""))}
              />
            </Field>
            <Field label={t("lang.name")}>
              <input className="form-input" placeholder="Russian" value={newName} onChange={(e) => setNewName(e.target.value)} />
            </Field>
          </div>
          <button className="btn btn-primary" disabled={busy || newCode.length !== 2 || !newName} onClick={handleAdd}>
            <Plus size={15} style={{ verticalAlign: "-2px", marginRight: 4 }} />
            {t("lang.addNew")}
          </button>
        </Section>

        <Section title={t("lang.import")} style={{ marginBottom: 0 }}>
          <InfoBanner message={t("lang.importHint")} />
          <Field label={t("lang.code")}>
            <input className="form-input" placeholder="de" maxLength={10} value={importLang} onChange={(e) => setImportLang(e.target.value)} />
          </Field>
          <label className="btn btn-primary" style={{ display: "inline-flex", alignItems: "center", gap: 6, cursor: "pointer" }}>
            <Upload size={15} />
            {t("lang.import")}
            <input
              type="file"
              accept=".xlsx,.json"
              style={{ display: "none" }}
              disabled={busy}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void handleImport(f);
                e.target.value = "";
              }}
            />
          </label>
        </Section>
      </div>

      <Section title={t("lang.missingTitle")}>
        {missing === null ? (
          <InfoBanner message={t("common.empty")} />
        ) : (
          <>
            <InfoBanner message={`${t("lang.missingCount")}: ${missing.unresolvedCount}`} />
            {missing.items.length > 0 && (
              <DataTable
                screenCode="IMPORT_RESULT_LIST"
                rowKey={(m) => `${m.locale}-${m.keyCode}`}
                rows={missing.items}
                showColumnPicker={false}
                fallbackColumns={IMPORT_RESULT_FALLBACK}
                renderers={{
                  row: (m) => m.locale,
                  message: (m) => (
                    <span style={{ fontFamily: "monospace" }}>
                      {m.keyCode} ({m.hitCount})
                    </span>
                  ),
                }}
              />
            )}
          </>
        )}
      </Section>
    </div>
  );
}
