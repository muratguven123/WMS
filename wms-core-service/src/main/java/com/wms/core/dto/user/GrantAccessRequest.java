package com.wms.core.dto.user;

import jakarta.validation.constraints.NotNull;

public record GrantAccessRequest(
        @NotNull Long companyId,
        Long locationId,
        @NotNull Long roleId
) {}
