package com.wms.outbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryIssueRequest {
    private Long shipmentId;
    private String shipmentNumber;
    private Long companyId;
    private Long warehouseLocationId;
    @Builder.Default
    private List<IssueLine> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IssueLine {
        private String productCode;
        private BigDecimal quantity;
    }
}
