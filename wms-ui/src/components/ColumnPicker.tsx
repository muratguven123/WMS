import { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, RotateCcw, Settings2 } from "lucide-react";
import type { ResolvedColumnDto } from "../api/services";
import { useI18n } from "../i18n/I18nContext";

interface ColumnPickerProps {
  columns: ResolvedColumnDto[];
  onChange: (columns: ResolvedColumnDto[]) => void;
  onReset: () => void;
  disabled?: boolean;
}

export function ColumnPicker({ columns, onChange, onReset, disabled }: ColumnPickerProps) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const sorted = [...columns].sort((a, b) => a.sequence - b.sequence);

  const toggleVisible = (key: string) => {
    const next = columns.map((c) => {
      if (c.key !== key || c.locked || c.forceVisible) return c;
      return { ...c, visible: !c.visible };
    });
    onChange(next);
  };

  const move = (key: string, dir: -1 | 1) => {
    const idx = sorted.findIndex((c) => c.key === key);
    const swapIdx = idx + dir;
    if (idx < 0 || swapIdx < 0 || swapIdx >= sorted.length) return;
    const a = sorted[idx];
    const b = sorted[swapIdx];
    if (a.locked && b.locked) return;

    const seqA = a.sequence;
    const seqB = b.sequence;
    const next = columns.map((c) => {
      if (c.key === a.key) return { ...c, sequence: seqB };
      if (c.key === b.key) return { ...c, sequence: seqA };
      return c;
    });
    onChange(next);
  };

  return (
    <div ref={ref} style={{ position: "relative", display: "inline-block" }}>
      <button
        type="button"
        className="btn btn-secondary"
        style={{ padding: "6px 10px" }}
        disabled={disabled}
        title={t("table.columns")}
        onClick={() => setOpen((v) => !v)}
      >
        <Settings2 size={14} />
      </button>
      {open && (
        <div
          className="card"
          style={{
            position: "absolute",
            right: 0,
            top: "100%",
            marginTop: 6,
            zIndex: 50,
            minWidth: 260,
            padding: 12,
            boxShadow: "0 8px 24px rgba(0,0,0,0.35)",
          }}
        >
          <div style={{ fontWeight: 600, marginBottom: 10, fontSize: "0.85rem" }}>{t("table.columns")}</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6, maxHeight: 320, overflowY: "auto" }}>
            {sorted.map((col) => {
              const pickerDisabled = col.locked || col.forceVisible;
              return (
                <div
                  key={col.key}
                  style={{ display: "flex", alignItems: "center", gap: 8, fontSize: "0.85rem" }}
                >
                  <input
                    type="checkbox"
                    checked={col.visible}
                    disabled={pickerDisabled || disabled}
                    onChange={() => toggleVisible(col.key)}
                  />
                  <span style={{ flex: 1, color: pickerDisabled ? "var(--text-muted)" : "var(--text-primary)" }}>
                    {t(col.labelKey)}
                  </span>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ padding: "2px 4px" }}
                    disabled={disabled}
                    onClick={() => move(col.key, -1)}
                  >
                    <ChevronUp size={12} />
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ padding: "2px 4px" }}
                    disabled={disabled}
                    onClick={() => move(col.key, 1)}
                  >
                    <ChevronDown size={12} />
                  </button>
                </div>
              );
            })}
          </div>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ marginTop: 10, width: "100%", fontSize: "0.8rem" }}
            disabled={disabled}
            onClick={() => {
              onReset();
              setOpen(false);
            }}
          >
            <RotateCcw size={12} style={{ verticalAlign: "-2px", marginRight: 6 }} />
            {t("table.resetColumns")}
          </button>
        </div>
      )}
    </div>
  );
}
