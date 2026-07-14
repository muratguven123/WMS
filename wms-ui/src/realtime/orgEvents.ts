/** Aynı sekmede Firma Yönetimi → Organizasyon Yapısı anlık yenileme (STOMP'a ek). */
export const COMPANIES_CHANGED_EVENT = "wms:companies-changed";

export function emitCompaniesChanged(): void {
  window.dispatchEvent(new CustomEvent(COMPANIES_CHANGED_EVENT));
}

export function onCompaniesChanged(handler: () => void): () => void {
  const listener = () => handler();
  window.addEventListener(COMPANIES_CHANGED_EVENT, listener);
  return () => window.removeEventListener(COMPANIES_CHANGED_EVENT, listener);
}
