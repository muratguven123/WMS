package com.wms.core.dto.org;

import com.wms.core.entity.enums.LocationType;
import jakarta.validation.constraints.Size;

public record UpdateLocationRequest(
        @Size(max = 200) String name,
        LocationType type,
        @Size(max = 50) String timezone,
        Long regionId
) {}
