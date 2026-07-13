package com.wms.inbound.dto;

import java.time.Instant;
import java.util.List;

public record ReceiptApprovedEvent(
        Long receiptId,
        Long inboundOrderId,
        Long companyId,
        Long warehouseLocationId,
        Instant approvedAt,
        List<ReceiptItemEventDto> items
) {}
