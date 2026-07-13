package com.wms.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InventoryIssueRequest(
        @NotNull Long shipmentId,
        @NotBlank String shipmentNumber,
        @NotNull Long companyId,
        @NotNull Long warehouseLocationId,
        @NotEmpty @Valid List<InventoryIssueLine> lines
) {}
