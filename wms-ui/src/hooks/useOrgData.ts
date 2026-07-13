import { useCallback, useEffect, useState } from "react";
import {
  describeError,
  orgService,
  type CompanySummaryDto,
  type LocationSummaryDto,
} from "../api/services";

export interface TenantCompany {
  id: number;
  name: string;
}

export interface TenantLocation {
  id: number;
  companyId: number;
  name: string;
  timezone?: string;
  active?: boolean;
}

/** Offline demo fallback — V14 seed UUID'leri ile uyumlu */
export const DEMO_COMPANIES: TenantCompany[] = [
  { id: 1, name: "Logistics Corp TR" },
  { id: 2, name: "Logistics Corp DE" },
];

export const DEMO_LOCATIONS: TenantLocation[] = [
  { id: 1, companyId: 1, name: "İstanbul Tuzla Deposu", timezone: "Europe/Istanbul", active: true },
  { id: 2, companyId: 2, name: "Berlin Central Deposu", timezone: "Europe/Berlin", active: true },
];

const isOffline = () => Boolean((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode);

function mapCompany(c: CompanySummaryDto): TenantCompany {
  return { id: c.id, name: c.name };
}

function mapLocation(l: LocationSummaryDto): TenantLocation {
  return {
    id: l.id,
    companyId: l.companyId,
    name: l.name,
    timezone: l.timezone,
    active: l.active,
  };
}

export function useOrgData() {
  const [companies, setCompanies] = useState<TenantCompany[]>([]);
  const [locations, setLocations] = useState<TenantLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCompanies = useCallback(async () => {
    if (isOffline()) {
      setCompanies(DEMO_COMPANIES);
      return DEMO_COMPANIES;
    }
    const data = await orgService.getCompanies();
    const mapped = data.map(mapCompany);
    setCompanies(mapped);
    return mapped;
  }, []);

  const loadLocations = useCallback(async (companyId: number) => {
    if (!companyId) {
      setLocations([]);
      return [];
    }
    if (isOffline()) {
      const demo = DEMO_LOCATIONS.filter((l) => l.companyId === companyId);
      setLocations(demo);
      return demo;
    }
    const data = await orgService.getLocations(companyId);
    const mapped = data.map(mapLocation);
    setLocations(mapped);
    return mapped;
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    loadCompanies()
      .catch((e) => {
        setError(describeError(e));
        setCompanies(DEMO_COMPANIES);
        return DEMO_COMPANIES;
      })
      .finally(() => setLoading(false));
  }, [loadCompanies]);

  return { companies, locations, loading, error, loadCompanies, loadLocations, setLocations };
}
