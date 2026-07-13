package com.wms.outbound.dto;

import java.util.List;

public record CloseBoxResponse(
        Long pickingListId,
        String sscc,
        List<Long> orderIds,
        String status
) {
}
