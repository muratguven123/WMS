import type { ResolvedColumnDto } from "../api/services";

/** Schema API başarısız olduğunda kullanılan yerel kolon tanımı. */
export function fallbackCol(
  key: string,
  labelKey: string,
  sequence: number,
  opts?: { locked?: boolean; dataType?: ResolvedColumnDto["dataType"] },
): ResolvedColumnDto {
  return {
    columnDefId: 0,
    key,
    labelKey,
    dataType: opts?.dataType ?? "STRING",
    visible: true,
    sequence,
    locked: opts?.locked ?? false,
    renderHint: null,
    forceHidden: false,
    forceVisible: false,
  };
}
