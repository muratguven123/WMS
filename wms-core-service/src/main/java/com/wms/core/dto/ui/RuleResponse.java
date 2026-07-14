package com.wms.core.dto.ui;

import com.wms.core.entity.FieldBehaviorRule;
import com.wms.core.entity.enums.FieldBehavior;

import java.time.OffsetDateTime;

/**
 * Kural ekleme/güncelleme sonrası dönen response DTO'su.
 *
 * <p>Entity'yi doğrudan serialize etmek yerine bu DTO kullanılır:
 * lazy relation'lar açılmaz, iç yapı sızıntısı olmaz.</p>
 */
public record RuleResponse(
        Long id,
        Long screenFieldId,
        String fieldKey,
        int priority,
        Long companyId,
        Long countryId,
        Long locationId,
        Long roleId,
        String operationType,
        Long warehouseId,
        String customerType,
        String productType,
        String transactionStatus,
        FieldBehavior behavior,
        String defaultValue,
        String validationRegex,
        String validationErrorMessageKey,
        OffsetDateTime updatedAt
) {

    /** Entity'den DTO üretir. */
    public static RuleResponse from(FieldBehaviorRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getScreenField().getId(),
                rule.getScreenField().getFieldKey(),
                rule.getPriority(),
                rule.getCompanyId(),
                rule.getCountryId(),
                rule.getLocationId(),
                rule.getRoleId(),
                rule.getOperationType(),
                rule.getWarehouseId(),
                rule.getCustomerType(),
                rule.getProductType(),
                rule.getTransactionStatus(),
                rule.getBehavior(),
                rule.getDefaultValue(),
                rule.getValidationRegex(),
                rule.getValidationErrorMessageKey(),
                rule.getUpdatedAt()
        );
    }
}
