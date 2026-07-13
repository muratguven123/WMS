package com.wms.finance.dto;

import java.time.LocalDate;

public record TcmbSyncResponse(
        boolean success,
        int inserted,
        int updated,
        int skipped,
        LocalDate rateDate
) {
    public static TcmbSyncResponse failed() {
        return new TcmbSyncResponse(false, 0, 0, 0, null);
    }

    public static TcmbSyncResponse from(TcmbUpsertResult result) {
        return new TcmbSyncResponse(true, result.inserted(), result.updated(), result.skipped(), result.rateDate());
    }
}
