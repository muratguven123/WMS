package com.wms.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateShipmentRequest(
        @NotBlank String shipmentNumber,
        @NotNull Long companyId,
        @NotNull Long warehouseLocationId,
        String carrierCode,
        BigDecimal totalWeight,
        @NotEmpty @Valid List<CreateShipmentBoxRequest> boxes
) {}
