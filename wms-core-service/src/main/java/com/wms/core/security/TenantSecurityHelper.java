package com.wms.core.security;

import org.springframework.stereotype.Component;

@Component("tenantSecurityHelper")
public class TenantSecurityHelper {

    public Long getCurrentUserId() {
        return TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);
    }
}
