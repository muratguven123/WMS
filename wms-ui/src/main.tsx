import { StrictMode, useCallback, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { Login } from './views/Login.tsx'
import { initAuth, isAuthenticated } from './api/wms-api-client'
import { I18nProvider, useI18n } from './i18n/I18nContext'
import { initDevModeFromUrl } from './auth/roles'

function RootApp() {
  const { t } = useI18n();
  const [authReady, setAuthReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const offline = Boolean((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode);

  const checkAuth = useCallback(async () => {
    if (offline) {
      setAuthenticated(true);
      setAuthReady(true);
      return;
    }
    const ok = isAuthenticated() || await initAuth();
    setAuthenticated(ok);
    setAuthReady(true);
  }, [offline]);

  useEffect(() => {
    void checkAuth();
    const onAuthRequired = () => {
      setAuthenticated(false);
    };
    window.addEventListener("wms:auth-required", onAuthRequired);
    return () => window.removeEventListener("wms:auth-required", onAuthRequired);
  }, [checkAuth]);

  if (!authReady) {
    return <div style={{ color: "var(--text-muted)", padding: 40 }}>{t("app.loading")}</div>;
  }

  if (!authenticated && !offline) {
    return <Login onSuccess={() => setAuthenticated(true)} />;
  }

  return (
    <App />
  );
}

async function bootstrap() {
  initDevModeFromUrl();

  const rootElement = document.getElementById('root')!;
  const root = createRoot(rootElement);

  const urlParams = new URLSearchParams(window.location.search);
  const forceDemo = urlParams.get("demo") === "true" || urlParams.get("mock") === "true";

  const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080";
  let useKeycloak = !forceDemo;

  if (useKeycloak) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);
      const response = await fetch(`${keycloakUrl}/realms/wms-realm/.well-known/openid-configuration`, {
        method: "GET",
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      if (!response.ok) throw new Error(`Realm status ${response.status}`);
    } catch (error) {
      console.warn("Keycloak unreachable — offline demo mode.", error);
      useKeycloak = false;
    }
  }

  if (!useKeycloak) {
    (window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode = true;
  }

  root.render(
    <StrictMode>
      <I18nProvider>
        <RootApp />
      </I18nProvider>
    </StrictMode>
  );
}

bootstrap();
