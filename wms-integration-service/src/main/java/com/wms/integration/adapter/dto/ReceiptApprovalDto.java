package com.wms.integration.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mal kabul onayı ERP senkronizasyon payload'u.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptApprovalDto {

    private Long receiptId;
    private String receiptNumber;
    private String inboundOrderNumber;
    private Long companyId;
    private Long warehouseLocationId;
    private List<ReceiptApprovalItemDto> approvedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptApprovalItemDto {
        private String productCode;
        private Object quantity;
        private String lotNumber;
        private String serialNumber;
        private Long recommendedStorageLocationId;
    }
}
