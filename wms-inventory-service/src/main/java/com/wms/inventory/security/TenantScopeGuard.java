package com.wms.inventory.security;

import com.wms.inventory.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class TenantScopeGuard {

    private TenantScopeGuard() {
    }

    public static TenantContext requireActiveWarehouse() {
        return TenantContextHolder.require();
    }

    public static Long requireWarehouseLocationId() {
        return requireActiveWarehouse().locationId();
    }

    public static Long requireCompanyId() {
        return requireActiveWarehouse().companyId();
    }

    public static void assertMatchesContext(Long warehouseLocationId) {
        if (warehouseLocationId == null) {
            return;
        }
        Long active = requireWarehouseLocationId();
        if (!active.equals(warehouseLocationId)) {
            throw new BusinessException(
                    "warehouseLocationId does not match active depot context. TENANT_SCOPE_MISMATCH",
                    HttpStatus.FORBIDDEN);
        }
    }

    public static void assertEntityBelongsToContext(Long entityWarehouseId, Long entityCompanyId) {
        TenantContext ctx = requireActiveWarehouse();
        if (entityWarehouseId != null && !ctx.locationId().equals(entityWarehouseId)) {
            throw new BusinessException(
                    "Resource belongs to another warehouse. TENANT_SCOPE_MISMATCH",
                    HttpStatus.FORBIDDEN);
        }
        if (entityCompanyId != null && !ctx.companyId().equals(entityCompanyId)) {
            throw new BusinessException(
                    "Resource belongs to another company. TENANT_SCOPE_MISMATCH",
                    HttpStatus.FORBIDDEN);
        }
    }
}
