import { keycloak } from "../api/wms-api-client";

export const WMS_ADMIN = "WMS_ADMIN";

/** JWT realm_access.roles listesi */
export function getRealmRoles(): string[] {
  const roles = keycloak.tokenParsed?.realm_access?.roles;
  return Array.isArray(roles) ? roles : [];
}

export function hasWmsRole(role: string): boolean {
  return getRealmRoles().includes(role);
}

export function isWmsAdmin(): boolean {
  return hasWmsRole(WMS_ADMIN);
}

/** Admin tüm menülere erişir; diğer roller yalnızca tanımlı listeye göre. */
export function canAccessAny(requiredRoles: string[]): boolean {
  if (isWmsAdmin()) return true;
  if (requiredRoles.length === 0) return true;
  const userRoles = getRealmRoles();
  return requiredRoles.some((role) => userRoles.includes(role));
}

/** Menü öğesi → gerekli Keycloak realm rolleri (WMS_ADMIN her zaman bypass). */
export const VIEW_ROLES: Record<string, string[]> = {
  dashboard: [],
  "rack-view": ["WAREHOUSE_MANAGER", "INVENTORY_CLERK"],
  inbound: ["INBOUND_CLERK", "WAREHOUSE_MANAGER"],
  transfer: ["INVENTORY_CLERK", "WAREHOUSE_MANAGER"],
  picking: ["PICKER", "WAREHOUSE_MANAGER"],
  shipping: ["SHIPPING_CLERK", "PACKER", "WAREHOUSE_MANAGER"],
  languages: ["LOCALIZATION_ADMIN"],
  "localization-settings": ["LOCALIZATION_ADMIN"],
  "address-master": ["LOCALIZATION_ADMIN"],
  "address-template-config": ["ADDRESS_CONFIG_ADMIN", "LOCALIZATION_ADMIN"],
  currency: ["FINANCE_USER", "FINANCE_MANAGER"],
  billing: ["FINANCE_USER", "FINANCE_MANAGER"],
  tax: ["FINANCE_MANAGER"],
  "org-hierarchy": [WMS_ADMIN],
  "company-management": [WMS_ADMIN],
  "country-management": [WMS_ADMIN],
  users: [WMS_ADMIN],
  "workflow-config": ["WAREHOUSE_MANAGER"],
  "approval-queue": ["WAREHOUSE_MANAGER"],
  "dynamic-ui": [WMS_ADMIN],
  "integration-monitor": ["INTEGRATION_ADMIN"],
  "realtime-test": [WMS_ADMIN],
  "audit-log": ["WAREHOUSE_MANAGER"],
};

export const FEATURES = {
  OPERATIONS: "operations",
  REALTIME: "realtime",
  APPROVALS: "approvals",
} as const;

export const VIEW_FEATURES: Record<string, string> = {
  "rack-view": FEATURES.OPERATIONS,
  inbound: FEATURES.OPERATIONS,
  transfer: FEATURES.OPERATIONS,
  picking: FEATURES.OPERATIONS,
  shipping: FEATURES.OPERATIONS,
  "workflow-config": FEATURES.OPERATIONS,
  "dynamic-ui": FEATURES.OPERATIONS,
  "audit-log": FEATURES.OPERATIONS,
  "realtime-test": FEATURES.REALTIME,
  "approval-queue": FEATURES.APPROVALS,
};

const DEV_MODE_STORAGE_KEY = "wms_dev_mode";

/** URL ?dev=true|false değerini localStorage ile senkronize eder. */
export function initDevModeFromUrl(): void {
  const devParam = new URLSearchParams(window.location.search).get("dev");
  if (devParam === "true") {
    localStorage.setItem(DEV_MODE_STORAGE_KEY, "true");
  } else if (devParam === "false") {
    localStorage.removeItem(DEV_MODE_STORAGE_KEY);
  }
}

export function isDevMode(): boolean {
  const isDevUrl = new URLSearchParams(window.location.search).get("dev") === "true";
  const isDevStorage = localStorage.getItem(DEV_MODE_STORAGE_KEY) === "true";
  return isDevUrl || isDevStorage;
}

export function isFeatureEnabled(featureName: string): boolean {
  if (isDevMode()) {
    return true;
  }

  // Firma/org anlık yenileme için WMS_ADMIN realtime kullanır
  if (featureName === FEATURES.REALTIME && isWmsAdmin()) {
    return true;
  }

  if (
    featureName === FEATURES.OPERATIONS ||
    featureName === FEATURES.REALTIME ||
    featureName === FEATURES.APPROVALS
  ) {
    return false;
  }

  return true;
}

export function canAccessView(viewId: string): boolean {
  const feature = VIEW_FEATURES[viewId];
  if (feature && !isFeatureEnabled(feature)) {
    return false;
  }
  return canAccessAny(VIEW_ROLES[viewId] ?? []);
}
