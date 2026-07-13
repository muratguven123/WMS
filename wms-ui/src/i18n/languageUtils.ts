import type { Language } from "../api/services";

/** Jackson active/isActive uyumluluğu */
export function isLangActive(l: Language): boolean {
  if (l.isActive === true) return true;
  if (l.isActive === false) return false;
  const legacy = l as Language & { active?: boolean };
  return legacy.active !== false;
}

export function isLangDefault(l: Language): boolean {
  if (l.isDefault === true) return true;
  if (l.isDefault === false) return false;
  const legacy = l as Language & { default?: boolean };
  return legacy.default === true;
}
