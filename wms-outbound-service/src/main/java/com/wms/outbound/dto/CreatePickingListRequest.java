package com.wms.outbound.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePickingListRequest(
        @NotEmpty List<Long> outboundOrderIds,
        @NotNull Long warehouseLocationId,
        @NotNull Long createdByUserId
) {
}
