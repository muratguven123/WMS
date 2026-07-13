package com.wms.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShipmentDispatchedEvent(
        Long shipmentId,
        String shipmentNumber,
        Long companyId,
        Long warehouseLocationId,
        String carrierCode,
        String trackingNumber,
        LocalDateTime dispatchedAt,
        List<String> boxSsccNumbers,
        List<IssuedProductLine> issuedItems
) {
    public record IssuedProductLine(String productCode, BigDecimal quantity) {}
}
