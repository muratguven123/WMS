package com.wms.outbound.dto;

import jakarta.validation.constraints.NotNull;

public record CloseBoxRequest(
        @NotNull Long pickingListId
) {
}
