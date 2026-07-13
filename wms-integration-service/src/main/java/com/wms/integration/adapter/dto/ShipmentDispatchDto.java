package com.wms.integration.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sevkiyat çıkışı ERP senkronizasyon payload'u.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDispatchDto {

    private Long shipmentId;
    private String shipmentNumber;
    private Long companyId;
    private Long warehouseLocationId;
    private String carrierCode;
    private String trackingNumber;
    private LocalDateTime dispatchedAt;
    private List<String> boxSsccNumbers;
    private List<ShipmentDispatchItemDto> issuedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentDispatchItemDto {
        private String productCode;
        private Object quantity;
    }
}
