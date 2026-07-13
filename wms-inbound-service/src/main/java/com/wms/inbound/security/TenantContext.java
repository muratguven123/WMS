package com.wms.inbound.security;


public record TenantContext(
        Long userId,
        Long companyId,
        Long locationId
) {

    public TenantContext {
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
        if (locationId == null) throw new IllegalArgumentException("locationId cannot be null");
    }
}
