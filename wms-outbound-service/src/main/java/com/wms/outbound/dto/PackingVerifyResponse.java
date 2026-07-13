package com.wms.outbound.dto;

import java.math.BigDecimal;

public record PackingVerifyResponse(
        Long pickingItemId,
        String productCode,
        BigDecimal targetQuantity,
        BigDecimal pickedQuantity,
        String status,
        String warningMessage
) {
}
