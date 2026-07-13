package com.wms.core.dto.org;

import com.wms.core.entity.enums.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull LocationType type,
        @NotBlank @Size(max = 50) String timezone,
        @NotNull Long regionId,
        /** Process config kopyalanacak şablon depo; null ise şirketteki ilk depo kullanılır. */
        Long templateLocationId
) {}
