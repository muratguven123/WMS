package com.wms.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StartReceiptRequest(
    @NotNull(message = "Inbound Order ID is required")
    Long inboundOrderId,

    @NotBlank(message = "Receipt number is required")
    String receiptNumber,

    @NotNull(message = "Received by User ID is required")
    Long receivedByUserId,

    @NotEmpty(message = "Receipt items cannot be empty")
    @Valid
    List<ReceiptItemRequest> items
) {
}
