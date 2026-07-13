package com.wms.core.security;


/**
 * İstek boyunca taşınan immutable tenant bağlamı.
 * Hangi kullanıcı, hangi şirket, hangi lokasyonda işlem yapıyor bilgisini tutar.
 */
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
