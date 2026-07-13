/**
 * Dinamik Form Renderer (İş İsteri 5)
 * Core servisin GET /api/ui/screens/{code}/schema çıktısını (ResolvedScreenDto)
 * alan davranışlarına göre canlı forma çevirir:
 *   HIDDEN → render edilmez, READ_ONLY → disabled,
 *   MANDATORY → * işareti + boş bırakılamaz, regex → canlı validasyon.
 */

import { useMemo, useState } from "react";
import type { ResolvedFieldDto, ResolvedScreenDto } from "../api/services";
import { useI18n } from "../i18n/I18nContext";

interface Props {
  schema: ResolvedScreenDto;
  onSubmit?: (values: Record<string, string>) => void;
  submitLabel?: string;
}

export function DynamicForm({ schema, onSubmit, submitLabel }: Props) {
  const { t } = useI18n();
  const visibleFields = useMemo(
    () => schema.fields.filter((f) => f.behavior !== "HIDDEN"),
    [schema],
  );

  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(visibleFields.map((f) => [f.fieldKey, f.defaultValue ?? ""])),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = (f: ResolvedFieldDto, value: string): string | null => {
    if (f.behavior === "MANDATORY" && !value.trim()) return t("validation.required");
    if (f.validationRegex && value) {
      try {
        if (!new RegExp(f.validationRegex).test(value)) {
          return f.validationErrorMessageKey ? t(f.validationErrorMessageKey) : t("validation.invalidFormat");
        }
      } catch {
        /* bozuk regex — validasyonu atla */
      }
    }
    return null;
  };

  const handleChange = (f: ResolvedFieldDto, value: string) => {
    setValues((v) => ({ ...v, [f.fieldKey]: value }));
    const err = validate(f, value);
    setErrors((e) => ({ ...e, [f.fieldKey]: err ?? "" }));
  };

  const handleSubmit = () => {
    const newErrors: Record<string, string> = {};
    for (const f of visibleFields) {
      if (f.behavior === "READ_ONLY") continue;
      const err = validate(f, values[f.fieldKey] ?? "");
      if (err) newErrors[f.fieldKey] = err;
    }
    setErrors(newErrors);
    if (Object.keys(newErrors).length === 0) onSubmit?.(values);
  };

  const inputType = (dataType: string): string => {
    switch (dataType?.toUpperCase()) {
      case "NUMBER": return "number";
      case "DATE": return "date";
      default: return "text";
    }
  };

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "0 20px" }}>
        {visibleFields.map((f) => {
          const readOnly = f.behavior === "READ_ONLY";
          const label = t(f.labelKey) === f.labelKey ? f.fieldKey : t(f.labelKey);
          return (
            <div key={f.fieldKey} className="form-group" style={{ marginBottom: 14 }}>
              <label>
                {label}
                {f.behavior === "MANDATORY" && <span style={{ color: "var(--neon-red)" }}> *</span>}
                {readOnly && (
                  <span className="badge badge-orange" style={{ marginLeft: 8, fontSize: "0.6rem" }}>{t("common.readOnly")}</span>
                )}
              </label>
              {f.dataType?.toUpperCase() === "BOOLEAN" ? (
                <select
                  className="form-select"
                  disabled={readOnly}
                  value={values[f.fieldKey] ?? ""}
                  onChange={(e) => handleChange(f, e.target.value)}
                >
                  <option value="">—</option>
                  <option value="true">{t("common.yes")}</option>
                  <option value="false">{t("common.no")}</option>
                </select>
              ) : (
                <input
                  className="form-input"
                  type={inputType(f.dataType)}
                  disabled={readOnly}
                  value={values[f.fieldKey] ?? ""}
                  onChange={(e) => handleChange(f, e.target.value)}
                  style={errors[f.fieldKey] ? { borderColor: "var(--neon-red)" } : undefined}
                />
              )}
              {errors[f.fieldKey] && (
                <div style={{ color: "var(--neon-red)", fontSize: "0.75rem", marginTop: 4 }}>{errors[f.fieldKey]}</div>
              )}
            </div>
          );
        })}
      </div>
      {onSubmit && (
        <button className="btn btn-primary" onClick={handleSubmit} style={{ marginTop: 8 }}>
          {submitLabel ?? t("common.save")}
        </button>
      )}
    </div>
  );
}
