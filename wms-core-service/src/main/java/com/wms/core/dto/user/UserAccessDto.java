package com.wms.core.dto.user;


public record UserAccessDto(
        Long id,
        Long companyId,
        String companyName,
        Long locationId,
        String locationName,
        Long roleId,
        String roleName
) {}
