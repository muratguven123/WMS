package com.wms.outbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record CreateShipmentBoxRequest(
        @NotNull Long outboundOrderId,
        @NotBlank String boxSsccNumber
) {}
