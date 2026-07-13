/**
 * Bağımlılıksız i18n + lokalizasyon context'i.
 *
 * İş İsteri 2: TR/EN gömülü; sunucudan gelen çeviriler üstüne yazılır,
 *              oturum kapatmadan (on-the-fly) dil değiştirilebilir.
 * İş İsteri 6: UTC → seçili saat dilimine göre gösterim (DST dahil, Intl API).
 * İş İsteri 7: Lokasyon bazlı tarih/saat/sayı formatı (localization-service'ten).
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { localizationService, type ActiveFormatResponse, type Language } from "../api/services";
import { BUILT_IN, type Dict } from "./translations";

const LANG_KEY = "wms.lang";
const TZ_KEY = "wms.timezone";

const DEFAULT_FORMAT: ActiveFormatResponse = {
  dateFormat: "dd.MM.yyyy",
  timeFormat: "HH:mm",
  decimalSeparator: ",",
  thousandSeparator: ".",
};

const FALLBACK_LANGUAGES: Language[] = [
  { id: 1, code: "tr", name: "Türkçe", isDefault: true, isActive: true },
  { id: 2, code: "en", name: "English", isActive: true },
];

export interface I18n {
  lang: string;
  setLang: (code: string) => void;
  t: (key: string) => string;
  languages: Language[];
  reloadLanguages: () => Promise<void>;
  reloadTranslations: (locale?: string) => Promise<void>;
  prefetchTranslations: (locale: string) => Promise<void>;
  /** Sunucu çevirisi yüklendi mi (yoksa gömülü sözlük mü)? */
  serverTranslationsActive: boolean;
  timezone: string;
  setTimezone: (tz: string) => void;
  format: ActiveFormatResponse;
  formatFromServer: boolean;
  formatDate: (isoUtc: string | Date | undefined | null) => string;
  formatDateTime: (isoUtc: string | Date | undefined | null) => string;
  formatNumber: (n: number | undefined | null, fractionDigits?: number) => string;
}

const Ctx = createContext<I18n | null>(null);

/** Backend deseni (dd.MM.yyyy HH:mm / MM/dd/yyyy hh:mm a) → Intl parçalarıyla üretim. */
function formatPattern(date: Date, pattern: string, tz: string): string {
  let parts: Intl.DateTimeFormatPart[];
  try {
    parts = new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: pattern.includes("a") || pattern.includes("h"),
    }).formatToParts(date);
  } catch {
    parts = new Intl.DateTimeFormat("en-US", {
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", second: "2-digit",
    }).formatToParts(date);
  }
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return pattern
    .replace(/yyyy/g, get("year"))
    .replace(/MM/g, get("month"))
    .replace(/dd/g, get("day"))
    .replace(/HH/g, get("hour").padStart(2, "0"))
    .replace(/hh/g, get("hour").padStart(2, "0"))
    .replace(/mm/g, get("minute"))
    .replace(/ss/g, get("second"))
    .replace(/\ba\b/g, get("dayPeriod"));
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<string>(() => localStorage.getItem(LANG_KEY) ?? "tr");
  const [serverDict, setServerDict] = useState<Dict>({});
  const [serverTranslationsActive, setServerActive] = useState(false);
  const [translationVersion, setTranslationVersion] = useState(0);
  const [languages, setLanguages] = useState<Language[]>(FALLBACK_LANGUAGES);
  const [timezone, setTimezoneState] = useState<string>(
    () => localStorage.getItem(TZ_KEY) ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  );
  const [format, setFormat] = useState<ActiveFormatResponse>(DEFAULT_FORMAT);
  const [formatFromServer, setFormatFromServer] = useState(false);
  const missingQueueRef = useRef<Set<string>>(new Set());
  const missingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const prefetchedRef = useRef<Record<string, Dict>>({});
  const langRef = useRef(lang);
  langRef.current = lang;

  const scheduleMissingReport = useCallback((locale: string) => {
    if (missingTimerRef.current) clearTimeout(missingTimerRef.current);
    missingTimerRef.current = setTimeout(() => {
      const keys = Array.from(missingQueueRef.current);
      missingQueueRef.current.clear();
      if (keys.length > 0) {
        void localizationService.reportMissingKeys(locale, keys);
      }
    }, 2000);
  }, []);

  const prefetchTranslations = useCallback(async (locale: string) => {
    const code = locale.toLowerCase();
    try {
      const dict = await localizationService.getTranslations(code, "UI");
      if (!dict || Object.keys(dict).length === 0) return;
      prefetchedRef.current[code] = dict;
      if (langRef.current === code) {
        setServerDict(dict);
        setServerActive(true);
      }
    } catch {
      /* servis henüz hazır olmayabilir */
    }
  }, []);

  const reloadTranslations = useCallback(async (locale?: string) => {
    if (locale) {
      await prefetchTranslations(locale);
      if (langRef.current === locale.toLowerCase()) {
        setTranslationVersion((v) => v + 1);
      }
      return;
    }
    setTranslationVersion((v) => v + 1);
  }, [prefetchTranslations]);

  // Sunucu çevirilerini çek — başarısızsa gömülü sözlükle devam (graceful degradation)
  useEffect(() => {
    let cancelled = false;
    const prefetched = prefetchedRef.current[lang];
    if (prefetched && Object.keys(prefetched).length > 0) {
      setServerDict(prefetched);
      setServerActive(true);
      return;
    }

    setServerActive(false);
    setServerDict({});
    localizationService
      .getTranslations(lang, "UI")
      .then((dict) => {
        if (!cancelled && dict && Object.keys(dict).length > 0) {
          prefetchedRef.current[lang] = dict;
          setServerDict(dict);
          setServerActive(true);
        }
      })
      .catch(() => {
        /* offline / servis kapalı — gömülü sözlük yeter */
      });
    return () => {
      cancelled = true;
    };
  }, [lang, translationVersion]);

  const reloadLanguages = useCallback(async () => {
    try {
      const list = await localizationService.getLanguages();
      if (list.length > 0) setLanguages(list);
    } catch {
      /* fallback listesi kalır */
    }
  }, []);

  useEffect(() => {
    void reloadLanguages();
    localizationService
      .getActiveFormat()
      .then((f) => {
        setFormat(f);
        setFormatFromServer(true);
      })
      .catch(() => {
        /* varsayılan format */
      });
  }, [reloadLanguages]);

  const setLang = useCallback((code: string) => {
    const normalized = code.toLowerCase();
    localStorage.setItem(LANG_KEY, normalized);
    const prefetched = prefetchedRef.current[normalized];
    if (prefetched && Object.keys(prefetched).length > 0) {
      setServerDict(prefetched);
      setServerActive(true);
    } else {
      setServerActive(false);
      setServerDict({});
    }
    setLangState(normalized);
  }, []);

  const setTimezone = useCallback((tz: string) => {
    localStorage.setItem(TZ_KEY, tz);
    setTimezoneState(tz);
  }, []);

  const t = useCallback(
    (key: string): string => {
      if (serverDict[key]) return serverDict[key];
      const builtIn = BUILT_IN[lang] ?? {};
      if (builtIn[key]) return builtIn[key];
      if (!serverTranslationsActive) {
        if (lang !== "tr" && lang !== "en") {
          const en = BUILT_IN.en ?? {};
          if (en[key]) return en[key];
        }
        if (lang !== "tr") {
          const tr = BUILT_IN.tr ?? {};
          if (tr[key]) return tr[key];
        }
      }
      if (serverTranslationsActive) {
        missingQueueRef.current.add(key);
        scheduleMissingReport(lang);
      }
      return key;
    },
    [lang, serverDict, serverTranslationsActive, scheduleMissingReport],
  );

  const toDate = (v: string | Date | undefined | null): Date | null => {
    if (!v) return null;
    const d = v instanceof Date ? v : new Date(v);
    return isNaN(d.getTime()) ? null : d;
  };

  const formatDate = useCallback(
    (v: string | Date | undefined | null) => {
      const d = toDate(v);
      return d ? formatPattern(d, format.dateFormat, timezone) : "—";
    },
    [format, timezone],
  );

  const formatDateTime = useCallback(
    (v: string | Date | undefined | null) => {
      const d = toDate(v);
      return d ? formatPattern(d, `${format.dateFormat} ${format.timeFormat}`, timezone) : "—";
    },
    [format, timezone],
  );

  const formatNumber = useCallback(
    (n: number | undefined | null, fractionDigits = 2): string => {
      if (n === undefined || n === null || isNaN(n)) return "—";
      const [intPart, fracPart] = n.toFixed(fractionDigits).split(".");
      const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, format.thousandSeparator);
      return fractionDigits > 0 ? `${grouped}${format.decimalSeparator}${fracPart}` : grouped;
    },
    [format],
  );

  const value = useMemo<I18n>(
    () => ({
      lang, setLang, t, languages, reloadLanguages, reloadTranslations, prefetchTranslations,
      serverTranslationsActive, timezone, setTimezone, format, formatFromServer,
      formatDate, formatDateTime, formatNumber,
    }),
    [lang, setLang, t, languages, reloadLanguages, reloadTranslations, prefetchTranslations,
     serverTranslationsActive, timezone, setTimezone, format, formatFromServer,
     formatDate, formatDateTime, formatNumber],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useI18n, I18nProvider içinde kullanılmalıdır");
  return ctx;
}
