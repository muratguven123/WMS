package com.wms.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReceiptQcRequest(
    @NotEmpty(message = "QC items cannot be empty")
    @Valid
    List<ReceiptItemQcRequest> items
) {
}
