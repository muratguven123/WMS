package com.wms.outbound.dto;

import jakarta.validation.constraints.NotNull;


public record AssignPickingListRequest(
        @NotNull Long assignedUserId
) {
}
