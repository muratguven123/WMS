package com.wms.core.dto.org;

import com.wms.core.entity.enums.LocationType;

public record LocationDetailDto(
        Long id,
        Long companyId,
        Long regionId,
        String name,
        LocationType type,
        String timezone,
        boolean active
) {}
