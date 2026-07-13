package com.wms.localization.security;


/**
 * İstek boyunca taşınan aktif depo/şirket bağlamı.
 */
public record TenantContext(
        Long locationId,
        Long companyId,
        Long userId
) {

    public TenantContext {
        if (locationId == null) {
            throw new IllegalArgumentException("locationId cannot be null");
        }
    }
}
