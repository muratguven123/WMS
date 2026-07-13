package com.wms.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderPackedEvent(
        Long orderId,
        String orderNumber,
        String sscc,
        LocalDateTime packedAt,
        List<PackedItemDto> items
) {
    public record PackedItemDto(
            String productCode,
            BigDecimal quantity
    ) {}
}
