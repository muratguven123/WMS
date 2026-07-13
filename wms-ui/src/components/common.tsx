import type { CSSProperties, ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info, Loader2 } from "lucide-react";
import { useI18n } from "../i18n/I18nContext";

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 24, gap: 16, flexWrap: "wrap" }}>
      <div>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 }}>{title}</h1>
        {subtitle && <p style={{ color: "var(--text-secondary)", fontSize: "0.9rem" }}>{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

const bannerBase: CSSProperties = {
  display: "flex", alignItems: "flex-start", gap: 10, padding: "12px 16px",
  borderRadius: 10, fontSize: "0.88rem", lineHeight: 1.45, marginBottom: 16,
};

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div style={{ ...bannerBase, background: "rgba(255,51,102,0.08)", border: "1px solid rgba(255,51,102,0.3)", color: "var(--neon-red)" }}>
      <AlertTriangle size={18} style={{ flexShrink: 0, marginTop: 1 }} />
      <span style={{ color: "var(--text-primary)" }}>{message}</span>
    </div>
  );
}

export function SuccessBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div style={{ ...bannerBase, background: "rgba(0,245,155,0.07)", border: "1px solid rgba(0,245,155,0.3)" }}>
      <CheckCircle2 size={18} color="var(--neon-green)" style={{ flexShrink: 0, marginTop: 1 }} />
      <span style={{ color: "var(--text-primary)" }}>{message}</span>
    </div>
  );
}

export function InfoBanner({ message }: { message: ReactNode }) {
  return (
    <div style={{ ...bannerBase, background: "rgba(0,210,255,0.06)", border: "1px solid rgba(0,210,255,0.25)" }}>
      <Info size={18} color="var(--neon-blue)" style={{ flexShrink: 0, marginTop: 1 }} />
      <span style={{ color: "var(--text-secondary)" }}>{message}</span>
    </div>
  );
}

export function Loading({ label }: { label?: string }) {
  const { t } = useI18n();
  const text = label ?? t("common.loading");
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, color: "var(--text-secondary)", padding: 20 }}>
      <Loader2 size={18} className="spin" style={{ animation: "spin 1s linear infinite" }} />
      {text}
    </div>
  );
}

export function StatCard({ label, value, color = "var(--neon-blue)" }: { label: string; value: ReactNode; color?: string }) {
  return (
    <div className="glass-card" style={{ padding: "18px 22px", minWidth: 150, flex: 1 }}>
      <div style={{ fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: 1, color: "var(--text-muted)", marginBottom: 6 }}>{label}</div>
      <div style={{ fontSize: "1.6rem", fontWeight: 700, color }}>{value}</div>
    </div>
  );
}

export function Section({ title, children, style }: { title: string; children: ReactNode; style?: CSSProperties }) {
  return (
    <div className="glass-card" style={{ padding: 24, marginBottom: 20, ...style }}>
      <h3 style={{ fontSize: "1.05rem", fontWeight: 600, color: "var(--text-primary)", marginBottom: 16 }}>{title}</h3>
      {children}
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="form-group" style={{ marginBottom: 14 }}>
      <label>{label}</label>
      {children}
    </div>
  );
}

export const grid2: CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "0 20px" };
