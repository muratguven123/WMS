package com.wms.core.service;

import com.wms.core.dto.ui.UiContext;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.util.DimensionCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;


/**
 * {@link UiContext} oluşturma fabrikası.
 *
 * <p>Tenant bilgisi {@link TenantContextHolder}'dan; rol, ülke, operasyon tipi
 * ve İş İsteri 2.1 (Madde 8.3) boyutları — depo, müşteri tipi, ürün tipi,
 * işlem durumu — HTTP query parametrelerinden okunur (schema API ve
 * {@code DynamicFormValidationAspect} ile ortak sözleşme).</p>
 *
 * <h3>Query parametre adları</h3>
 * <pre>
 * roleId, countryId, operationType,
 * warehouseId, customerType, productType, transactionStatus
 * </pre>
 *
 * <p>String boyut kodları {@link DimensionCode#normalizeOrNull} ile normalize
 * edilir (trim + UPPER) — kural yazma yolundaki normalizasyonla simetrik olduğu
 * için eşleşme büyük/küçük harf duyarsız davranır.</p>
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
                param(request, "operationType"),
                parseUuid(request, "warehouseId"),
                DimensionCode.normalizeOrNull(param(request, "customerType")),
                DimensionCode.normalizeOrNull(param(request, "productType")),
                DimensionCode.normalizeOrNull(param(request, "transactionStatus"))
        );
    }

    /**
     * Geriye dönük uyumlu imza — yeni boyutlar null bırakılır.
     */
    public UiContext fromTenant(Long locationId, Long companyId, Long roleId,
                                Long countryId, String operationType) {
        return fromTenant(locationId, companyId, roleId, countryId, operationType,
                null, null, null, null);
    }

    /**
     * Tüm boyutları içeren tam imza (İş İsteri 2.1, Madde 8.3).
     * String boyut kodları normalize edilir.
     */
    public UiContext fromTenant(Long locationId, Long companyId, Long roleId,
                                Long countryId, String operationType,
                                Long warehouseId, String customerType,
                                String productType, String transactionStatus) {
        return new UiContext(
                locationId, roleId, companyId, countryId, operationType,
                warehouseId,
                DimensionCode.normalizeOrNull(customerType),
                DimensionCode.normalizeOrNull(productType),
                DimensionCode.normalizeOrNull(transactionStatus));
    }

    private String param(HttpServletRequest request, String name) {
        return request != null ? request.getParameter(name) : null;
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
