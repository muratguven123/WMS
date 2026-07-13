import { useCallback, useEffect, useMemo, useState } from "react";
import {
  dynamicUiService,
  type ColumnPreference,
  type ResolvedColumnDto,
  type ResolvedTableSchemaDto,
} from "../api/services";

function storageKey(screenCode: string) {
  return `wms.tableSchema.${screenCode}`;
}

function sortColumns(cols: ResolvedColumnDto[]) {
  return [...cols].sort((a, b) => a.sequence - b.sequence);
}

export function useTableSchema(
  screenCode: string,
  fallbackColumns: ResolvedColumnDto[],
  opts?: { roleId?: number; countryId?: number },
) {
  const [columns, setColumns] = useState<ResolvedColumnDto[]>(() => sortColumns(fallbackColumns));
  const [loading, setLoading] = useState(true);
  const [schemaSource, setSchemaSource] = useState<"server" | "cache" | "fallback">("fallback");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const schema = await dynamicUiService.getTableSchema(screenCode, opts);
      const cols = Array.isArray(schema?.columns) ? schema.columns : fallbackColumns;
      const sorted = sortColumns(cols);
      setColumns(sorted);
      setSchemaSource(Array.isArray(schema?.columns) ? "server" : "fallback");
      if (Array.isArray(schema?.columns)) {
        localStorage.setItem(storageKey(screenCode), JSON.stringify(schema));
      }
    } catch {
      const raw = localStorage.getItem(storageKey(screenCode));
      if (raw) {
        try {
          const cached = JSON.parse(raw) as ResolvedTableSchemaDto;
          setColumns(sortColumns(cached.columns));
          setSchemaSource("cache");
        } catch {
          setColumns(sortColumns(fallbackColumns));
          setSchemaSource("fallback");
        }
      } else {
        setColumns(sortColumns(fallbackColumns));
        setSchemaSource("fallback");
      }
    } finally {
      setLoading(false);
    }
  }, [screenCode, fallbackColumns, opts?.roleId, opts?.countryId]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleColumns = useMemo(
    () => columns.filter((c) => c.visible && !c.forceHidden),
    [columns],
  );

  const pickerColumns = useMemo(
    () => columns.filter((c) => !c.forceHidden),
    [columns],
  );

  const savePreferences = useCallback(
    async (nextColumns: ResolvedColumnDto[]) => {
      const prefs: ColumnPreference[] = nextColumns
        .filter((c) => !c.forceHidden && !c.forceVisible)
        .map((c) => ({
          key: c.key,
          visible: c.visible,
          sequence: c.sequence,
          width: null,
        }));
      setColumns(sortColumns(nextColumns));
      await dynamicUiService.putTablePreferences(screenCode, prefs);
      await load();
    },
    [screenCode, load],
  );

  const resetPreferences = useCallback(async () => {
    await dynamicUiService.deleteTablePreferences(screenCode);
    await load();
  }, [screenCode, load]);

  return {
    columns,
    visibleColumns,
    pickerColumns,
    loading,
    schemaSource,
    savePreferences,
    resetPreferences,
    reload: load,
  };
}
