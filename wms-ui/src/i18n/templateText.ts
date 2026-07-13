/**
 * Şablon (CountryAddressTemplate vb.) kaynaklı metinlerin generic çözümü.
 *
 * Kural (İş İsteri 10): sabit alan yalnızca ülkedir; diğer tüm alanlar
 * şablondan gelir ve UI hiçbir field_key'e özel etiket/mesaj hardcode etmez.
 * Çeviri bulunamazsa ekrana asla ham i18n anahtarı yazılmaz — generic
 * fallback zinciri uygulanır.
 */

type Translate = (key: string) => string;

interface TemplateFieldLike {
  fieldKey: string;
  fieldLabelKey?: string | null;
  errorMessageKey?: string | null;
}

/** t() anahtarı çözemediğinde anahtarın kendisini döndürür; bunu "çözülemedi" sayar. */
function resolveKey(t: Translate, key?: string | null): string | undefined {
  if (!key) return undefined;
  const value = t(key);
  return value !== key ? value : undefined;
}

/** "zip_code" → "Zip Code" — çeviri yoksa son çare, ham anahtar asla gösterilmez. */
function humanizeFieldKey(fieldKey: string): string {
  return fieldKey
    .split(/[_\-\s]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/**
 * Alan etiketi: fieldLabelKey → eski "field.<key>.label" anahtarı → humanize.
 */
export function templateFieldLabel(t: Translate, tpl: TemplateFieldLike): string {
  return (
    resolveKey(t, tpl.fieldLabelKey) ??
    resolveKey(t, `field.${tpl.fieldKey}.label`) ??
    humanizeFieldKey(tpl.fieldKey)
  );
}

/**
 * Doğrulama mesajı: errorMessageKey → "validation.<key>.<kind>" →
 * generic validation.required / validation.invalidFormat.
 */
export function templateFieldError(
  t: Translate,
  tpl: TemplateFieldLike,
  kind: "required" | "invalid",
): string {
  return (
    resolveKey(t, tpl.errorMessageKey) ??
    resolveKey(t, `validation.${tpl.fieldKey}.${kind}`) ??
    t(kind === "required" ? "validation.required" : "validation.invalidFormat")
  );
}
