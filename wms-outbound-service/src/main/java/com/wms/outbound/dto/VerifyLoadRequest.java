package com.wms.outbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyLoadRequest(
        @NotNull(message = "Shipment ID must not be null")
        Long shipmentId,

        @NotBlank(message = "Box SSCC number must not be blank")
        String boxSsccNumber
) {}
