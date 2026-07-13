import type { ReactNode } from "react";
import type { ResolvedColumnDto } from "../api/services";
import { useI18n } from "../i18n/I18nContext";
import { useTableSchema } from "../hooks/useTableSchema";
import { ColumnPicker } from "./ColumnPicker";
import { Loading } from "./common";

export interface DataTableProps<T> {
  screenCode: string;
  rowKey: (row: T) => string | number;
  rows: T[];
  renderers: Record<string, (row: T) => ReactNode>;
  fallbackColumns: ResolvedColumnDto[];
  loading?: boolean;
  emptyMessage?: string;
  roleId?: number;
  countryId?: number;
  showColumnPicker?: boolean;
  rowProps?: (row: T) => React.HTMLAttributes<HTMLTableRowElement>;
}

export function DataTable<T>({
  screenCode,
  rowKey,
  rows,
  renderers,
  fallbackColumns,
  loading: rowsLoading,
  emptyMessage,
  roleId,
  countryId,
  showColumnPicker = true,
  rowProps,
}: DataTableProps<T>) {
  const { t } = useI18n();
  const {
    visibleColumns,
    pickerColumns,
    loading: schemaLoading,
    savePreferences,
    resetPreferences,
  } = useTableSchema(screenCode, fallbackColumns, { roleId, countryId });

  const safeRows = Array.isArray(rows) ? rows : [];
  const displayColumns = (Array.isArray(visibleColumns) ? visibleColumns : [])
    .filter((c) => renderers[c.key] != null)
    .sort((a, b) => a.sequence - b.sequence);

  const busy = schemaLoading || rowsLoading;

  if (busy && safeRows.length === 0) {
    return <Loading label={t("common.loading")} />;
  }

  return (
    <div>
      {showColumnPicker && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
          <ColumnPicker
            columns={Array.isArray(pickerColumns) ? pickerColumns : []}
            disabled={schemaLoading}
            onChange={(next) => void savePreferences(next)}
            onReset={() => void resetPreferences()}
          />
        </div>
      )}
      <div className="table-container">
        <table className="wms-table">
          <thead>
            <tr>
              {displayColumns.map((col) => (
                <th key={col.key}>{t(col.labelKey)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {safeRows.length === 0 ? (
              <tr>
                <td colSpan={Math.max(displayColumns.length, 1)} style={{ textAlign: "center", color: "var(--text-muted)" }}>
                  {emptyMessage ?? t("common.empty")}
                </td>
              </tr>
            ) : (
              safeRows.map((row) => (
                <tr key={rowKey(row)} {...rowProps?.(row)}>
                  {displayColumns.map((col) => (
                    <td key={col.key}>{renderers[col.key](row)}</td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
