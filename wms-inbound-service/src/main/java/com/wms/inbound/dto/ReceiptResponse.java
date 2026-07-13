package com.wms.inbound.dto;

import com.wms.inbound.entity.enums.ReceiptStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
    Long id,
    Long inboundOrderId,
    String receiptNumber,
    Long receivedByUserId,
    LocalDateTime receivedAt,
    ReceiptStatus status,
    List<ReceiptItemResponse> items
) {
}
