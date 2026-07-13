package com.wms.inbound.dto;

import java.math.BigDecimal;

public record ReceiptItemEventDto(
        String productCode,
        BigDecimal quantity,
        String lotNumber,
        String serialNumber,
        Long recommendedStorageLocationId
) {}
