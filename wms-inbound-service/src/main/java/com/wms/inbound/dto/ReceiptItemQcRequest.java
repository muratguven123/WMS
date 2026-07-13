package com.wms.inbound.dto;

import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReceiptItemQcRequest(
    @NotBlank(message = "Product code is required")
    String productCode,

    @NotNull(message = "QC status is required")
    ReceiptItemQcStatus qcStatus,

    String lotNumber,
    String serialNumber
) {
}
