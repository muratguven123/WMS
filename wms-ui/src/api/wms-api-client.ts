/**
 * WMS UI — Axios API Client
 * Keycloak JWT + custom login + multi-tenant headers.
 */

import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from "axios";
import Keycloak from "keycloak-js";

const TOKEN_STORAGE_KEY = "wms.auth.tokens";

/** Docker/nginx: boş → same-origin; dev: localhost:8081 */
function resolveCoreApiBase(): string {
  const env = import.meta.env.VITE_API_BASE_URL;
  if (env != null && String(env).trim() !== "") {
    return String(env).replace(/\/$/, "");
  }
  return import.meta.env.PROD ? "" : "http://localhost:8081";
}

const CORE_API = resolveCoreApiBase();

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  expiresAt: number;
}

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080",
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "wms-realm",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "wms-ui",
});

function parseJwt(token: string): Record<string, unknown> {
  try {
    return JSON.parse(atob(token.split(".")[1]));
  } catch {
    return {};
  }
}

const LEGACY_WMS_USER_ID_CLAIMS = new Set([
  "55555555-0000-0000-0000-000000000001",
]);

/** JWT wms_user_id claim'inden sayısal kullanıcı kimliği çözümlenir (legacy UUID destekli). */
export function resolveWmsUserIdFromClaim(claim: unknown): number | null {
  if (typeof claim !== "string") return null;
  const trimmed = claim.trim();
  if (!trimmed) return null;
  if (/^\d+$/.test(trimmed)) return Number(trimmed);
  if (LEGACY_WMS_USER_ID_CLAIMS.has(trimmed.toLowerCase())) return 1;
  return null;
}

/** Mikroservislerin beklediği wms_user_id claim'i JWT'de var mı? */
export function hasValidWmsUserIdClaim(accessToken: string): boolean {
  return resolveWmsUserIdFromClaim(parseJwt(accessToken).wms_user_id) != null;
}

export function getStoredTokens(): StoredTokens | null {
  const raw = sessionStorage.getItem(TOKEN_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredTokens;
  } catch {
    return null;
  }
}

export function storeTokens(tokens: StoredTokens): void {
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens));
}

export function clearStoredTokens(): void {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY);
}

export function applyTokensToKeycloak(tokens: StoredTokens): void {
  keycloak.token = tokens.accessToken;
  keycloak.refreshToken = tokens.refreshToken;
  keycloak.idToken = tokens.idToken;
  keycloak.tokenParsed = parseJwt(tokens.accessToken);
  keycloak.refreshTokenParsed = tokens.refreshToken ? parseJwt(tokens.refreshToken) : undefined;
  keycloak.authenticated = true;
  keycloak.subject = (keycloak.tokenParsed?.sub as string) ?? undefined;
}

function normalizeTokenResponse(data: Record<string, unknown>): StoredTokens {
  const accessToken = String(data.accessToken ?? data.access_token ?? "");
  const refreshToken = String(data.refreshToken ?? data.refresh_token ?? "");
  const idToken = data.idToken ?? data.id_token;
  const expiresIn = Number(data.expiresIn ?? data.expires_in ?? 900);
  if (!accessToken) {
    throw new Error("Sunucu access token döndürmedi");
  }
  return {
    accessToken,
    refreshToken,
    idToken: idToken ? String(idToken) : undefined,
    expiresAt: Date.now() + expiresIn * 1000,
  };
}

/** Backend /api/auth/refresh ile token yenile — keycloak.updateToken kullanılmaz. */
export async function refreshAccessToken(): Promise<StoredTokens | null> {
  const stored = getStoredTokens();
  if (!stored?.refreshToken) return null;

  const refreshUrl = CORE_API ? `${CORE_API}/api/auth/refresh` : "/api/auth/refresh";
  try {
    const { data } = await axios.post<Record<string, unknown>>(refreshUrl, {
      refreshToken: stored.refreshToken,
    });
    const tokens = normalizeTokenResponse(data);
    storeTokens(tokens);
    applyTokensToKeycloak(tokens);
    return tokens;
  } catch (err) {
    const code = (err as AxiosError<{ errorCode?: string }>).response?.data?.errorCode;
    if (code === "AUTH_RELOGIN_REQUIRED") {
      clearStoredTokens();
      keycloak.authenticated = false;
    }
    throw err;
  }
}

/**
 * Geçerli access token döner; süresi dolmak üzeyse backend refresh kullanır.
 * Custom login akışında keycloak-js updateToken ÇAĞRILMAMALI (init edilmediği için hata verir).
 */
export async function ensureValidAccessToken(minValiditySec = 30): Promise<string | null> {
  if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
    return null;
  }

  const stored = getStoredTokens();
  if (!stored?.accessToken) return null;

  const msLeft = stored.expiresAt - Date.now();
  const claimValid = hasValidWmsUserIdClaim(stored.accessToken);

  if (msLeft > minValiditySec * 1000 && claimValid) {
    applyTokensToKeycloak(stored);
    return stored.accessToken;
  }

  try {
    const refreshed = await refreshAccessToken();
    if (refreshed?.accessToken && hasValidWmsUserIdClaim(refreshed.accessToken)) {
      return refreshed.accessToken;
    }
    clearStoredTokens();
    keycloak.authenticated = false;
    window.dispatchEvent(new CustomEvent("wms:auth-required"));
    return null;
  } catch {
    clearStoredTokens();
    keycloak.authenticated = false;
    window.dispatchEvent(new CustomEvent("wms:auth-required"));
    return null;
  }
}

export function isAuthenticated(): boolean {
  if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return true;
  const stored = getStoredTokens();
  if (stored && stored.expiresAt > Date.now() && hasValidWmsUserIdClaim(stored.accessToken)) {
    applyTokensToKeycloak(stored);
    return true;
  }
  return Boolean(
    keycloak.authenticated &&
      keycloak.token &&
      hasValidWmsUserIdClaim(keycloak.token),
  );
}

/**
 * Uygulama açılışında çağrılır — kayıtlı token varsa oturumu geri yükler.
 */
export async function initAuth(): Promise<boolean> {
  const stored = getStoredTokens();
  if (!stored?.accessToken) return false;
  const token = await ensureValidAccessToken(0);
  return token != null;
}

export async function loginWithCredentials(username: string, password: string): Promise<void> {
  const url = CORE_API ? `${CORE_API}/api/auth/login` : "/api/auth/login";
  const body = new URLSearchParams();
  body.set("username", username);
  body.set("password", password);

  const { data } = await axios.post<Record<string, unknown>>(url, body, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });

  const tokens = normalizeTokenResponse(data);
  storeTokens(tokens);
  applyTokensToKeycloak(tokens);
  if (!hasValidWmsUserIdClaim(tokens.accessToken)) {
    clearStoredTokens();
    throw new Error("Giriş başarılı ancak oturum claim'i senkronize edilemedi — core-service ve Keycloak ayakta mı?");
  }
}

export async function logoutUser(): Promise<void> {
  clearStoredTokens();
  tenantContext.clear();
  keycloak.authenticated = false;
  keycloak.token = undefined;
  keycloak.refreshToken = undefined;
}

const STORAGE_KEYS = {
  companyId: "wms.activeCompanyId",
  locationId: "wms.activeLocationId",
} as const;

function parseStoredId(value: string | null): number | null {
  if (!value) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export const tenantContext = {
  getCompanyId: (): number | null => parseStoredId(localStorage.getItem(STORAGE_KEYS.companyId)),
  getLocationId: (): number | null => parseStoredId(localStorage.getItem(STORAGE_KEYS.locationId)),
  set(companyId: number, locationId: number): void {
    localStorage.setItem(STORAGE_KEYS.companyId, String(companyId));
    localStorage.setItem(STORAGE_KEYS.locationId, String(locationId));
  },
  clear(): void {
    localStorage.removeItem(STORAGE_KEYS.companyId);
    localStorage.removeItem(STORAGE_KEYS.locationId);
  },
};

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const api: AxiosInstance = axios.create({
  baseURL: CORE_API,
  timeout: 30_000,
});

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  if (!(window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
    const token = await ensureValidAccessToken(30);
    if (!token) {
      return Promise.reject(new axios.Cancel("Oturum gerekli"));
    }
    config.headers.set("Authorization", `Bearer ${token}`);
  }

  const companyId = tenantContext.getCompanyId();
  const locationId = tenantContext.getLocationId();
  if (companyId != null) config.headers.set("X-Active-Company-ID", String(companyId));
  if (locationId != null) config.headers.set("X-Active-Location-ID", String(locationId));
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;

    if (status === 401 && !(window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode && original && !original._retry) {
      original._retry = true;
      try {
        const refreshed = await refreshAccessToken();
        if (refreshed?.accessToken) {
          original.headers.set("Authorization", `Bearer ${refreshed.accessToken}`);
          return api(original);
        }
      } catch {
        clearStoredTokens();
      }
      window.dispatchEvent(new CustomEvent("wms:auth-required"));
    }

    if (status === 403) {
      window.dispatchEvent(
        new CustomEvent("wms:tenant-forbidden", {
          detail: {
            companyId: tenantContext.getCompanyId(),
            locationId: tenantContext.getLocationId(),
            url: original?.url,
          },
        }),
      );
    }

    return Promise.reject(error);
  },
);
