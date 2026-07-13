/**
 * Generic dinamik adres alanı bileşeni.
 *
 * Şablondan gelen alanları serbest metin olarak render eder.
 * Ülke seçimi AddressMaster'da ayrı bir dropdown olarak yapılır.
 */
import {
  type CountryAddressTemplateDto,
} from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { templateFieldLabel } from "../i18n/templateText";
import { Field } from "./common";

export interface DynamicAddressFieldProps {
  template: CountryAddressTemplateDto;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string | null;
}

export function DynamicAddressField({
  template,
  value,
  onChange,
  disabled,
  error,
}: DynamicAddressFieldProps) {
  const { t } = useI18n();

  if (template.fieldType === "FIXED") return null;

  const label = templateFieldLabel(t, template);

  let htmlPattern: string | undefined;
  if (template.validationRegex) {
    try {
      void new RegExp(template.validationRegex);
      htmlPattern = template.validationRegex;
    } catch {
      htmlPattern = undefined;
    }
  }

  return (
    <Field label={label}>
      <input
        className="form-input"
        value={value}
        disabled={disabled}
        pattern={htmlPattern}
        onChange={(e) => onChange(e.target.value)}
      />
      {error && (
        <div style={{ fontSize: "0.78rem", color: "var(--neon-red)", marginTop: 4 }}>{error}</div>
      )}
    </Field>
  );
}
