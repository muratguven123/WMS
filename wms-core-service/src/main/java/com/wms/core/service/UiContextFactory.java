package com.wms.core.service;

import com.wms.core.dto.ui.UiContext;
import com.wms.core.security.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;


/**
 * {@link UiContext} oluşturma fabrikası.
 *
 * <p>Tenant bilgisi {@link TenantContextHolder}'dan; rol, ülke ve operasyon tipi
 * HTTP query parametrelerinden okunur (schema API ve validasyon aspect ile uyumlu).</p>
 */
@Component
public class UiContextFactory {

    public UiContext fromCurrentRequest(HttpServletRequest request) {
        var tenant = TenantContextHolder.require();
        return new UiContext(
                tenant.locationId(),
                parseUuid(request, "roleId"),
                tenant.companyId(),
                parseUuid(request, "countryId"),
                request != null ? request.getParameter("operationType") : null
        );
    }

    public UiContext fromTenant(Long locationId, Long companyId, Long roleId,
                                Long countryId, String operationType) {
        return new UiContext(locationId, roleId, companyId, countryId, operationType);
    }

    private Long parseUuid(HttpServletRequest request, String param) {
        if (request == null) {
            return null;
        }
        String value = request.getParameter(param);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
