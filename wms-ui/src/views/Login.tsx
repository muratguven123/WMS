import { useState, type CSSProperties, type FormEvent } from "react";
import { Network, LogIn } from "lucide-react";
import { loginWithCredentials, isAuthenticated } from "../api/wms-api-client";
import { describeError } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { ErrorBanner } from "../components/common";

interface LoginProps {
  onSuccess: () => void;
}

export function Login({ onSuccess }: LoginProps) {
  const { t } = useI18n();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await loginWithCredentials(username.trim(), password);
      if (!isAuthenticated()) {
        throw new Error("Oturum oluşturulamadı");
      }
      onSuccess();
    } catch (e) {
      const msg = describeError(e);
      setError(msg.includes("401") || msg.includes("AUTH") ? t("login.error") : msg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={styles.page}>
      <div className="glass-card" style={styles.card}>
        <div style={styles.header}>
          <Network size={36} color="var(--neon-blue)" />
          <h1 style={styles.title}>{t("login.title")}</h1>
          <p style={styles.subtitle}>{t("login.subtitle")}</p>
        </div>

        <ErrorBanner message={error} />

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.label}>
            {t("login.username")}
            <input
              className="form-input"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>
          <label style={styles.label}>
            {t("login.password")}
            <input
              className="form-input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy} style={styles.submit}>
            <LogIn size={16} style={{ marginRight: 8, verticalAlign: "-2px" }} />
            {busy ? t("common.loading") : t("login.submit")}
          </button>
        </form>

        <p style={styles.hint}>{t("login.demoHint")}</p>
      </div>
    </div>
  );
}

const styles: Record<string, CSSProperties> = {
  page: {
    minHeight: "100vh",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "radial-gradient(ellipse at top, #0e1a30 0%, var(--bg-primary) 60%)",
    padding: 24,
  },
  card: {
    width: "100%",
    maxWidth: 420,
    padding: "40px 36px",
    borderRadius: 16,
  },
  header: {
    textAlign: "center",
    marginBottom: 28,
  },
  title: {
    fontSize: 28,
    fontWeight: 700,
    marginTop: 12,
    background: "linear-gradient(90deg, var(--neon-blue), var(--neon-green))",
    WebkitBackgroundClip: "text",
    WebkitTextFillColor: "transparent",
  },
  subtitle: {
    color: "var(--text-secondary)",
    marginTop: 6,
    fontSize: 14,
  },
  form: {
    display: "flex",
    flexDirection: "column",
    gap: 16,
  },
  label: {
    display: "flex",
    flexDirection: "column",
    gap: 6,
    fontSize: 13,
    color: "var(--text-secondary)",
  },
  submit: {
    marginTop: 8,
    width: "100%",
    justifyContent: "center",
    display: "flex",
    alignItems: "center",
  },
  hint: {
    marginTop: 20,
    textAlign: "center",
    fontSize: 12,
    color: "var(--text-muted)",
  },
};
