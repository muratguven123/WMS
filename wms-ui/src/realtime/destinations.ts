export const stompDestinations = {
  stock: (companyId: number, locationId: number) =>
    `/topic/company.${companyId}.location.${locationId}.stock`,
  locations: (companyId: number, locationId: number) =>
    `/topic/company.${companyId}.location.${locationId}.locations`,
  approvals: (companyId: number) => `/topic/company.${companyId}.approvals`,
  userApprovals: (userId: string | number) => `/queue/user.${userId}.approvals`,
  integrations: (companyId: number) => `/topic/company.${companyId}.integrations`,
  userTasks: (userId: string | number) => `/queue/user.${userId}.tasks`,
  tasks: (companyId: number, locationId: number) =>
    `/topic/company.${companyId}.location.${locationId}.tasks`,
  operators: (companyId: number, locationId: number) =>
    `/topic/company.${companyId}.location.${locationId}.operators`,
  /** Firma CRUD sonrası Organizasyon Yapısı / header yenileme */
  orgCompanies: () => `/topic/org.companies`,
};

function wsBase(): string {
  if (import.meta.env.PROD) {
    const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${proto}//${window.location.host}`;
  }
  return "ws://localhost:5173";
}

export function buildWsUrl(accessToken: string): string {
  return `${wsBase()}/ws?access_token=${encodeURIComponent(accessToken)}`;
}
