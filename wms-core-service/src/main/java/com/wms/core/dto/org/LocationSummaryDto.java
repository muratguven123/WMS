package com.wms.core.dto.org;


public record LocationSummaryDto(
        Long id,
        Long companyId,
        String name,
        String timezone,
        boolean active
) {}
