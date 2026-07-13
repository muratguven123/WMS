package com.wms.finance.dto;

import java.time.LocalDate;

public record TcmbUpsertResult(
        int inserted,
        int updated,
        int skipped,
        LocalDate rateDate
) {
    public static TcmbUpsertResult empty() {
        return new TcmbUpsertResult(0, 0, 0, null);
    }
}
