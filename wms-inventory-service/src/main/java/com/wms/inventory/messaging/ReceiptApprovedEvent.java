package com.wms.inventory.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kafka mesajı — inbound servisindeki {@code ReceiptApprovedEvent} ile aynı JSON şeması.
 */
public record ReceiptApprovedEvent(
        Long receiptId,
        Long inboundOrderId,
        Long companyId,
        Long warehouseLocationId,
        Instant approvedAt,
        List<ReceiptItemEventDto> items
) {
}
