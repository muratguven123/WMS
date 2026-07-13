package com.wms.inbound.dto;

import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import java.math.BigDecimal;

public record ReceiptItemResponse(
    Long id,
    String productCode,
    BigDecimal quantity,
    String lotNumber,
    String serialNumber,
    ReceiptItemQcStatus qcStatus
) {
}
