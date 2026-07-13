package com.wms.localization.dto.address;

import com.wms.localization.domain.address.AddressTemplateField;
import lombok.Builder;

/**
 * Ülkeden bağımsız adres alan kataloğu DTO'su.
 */
@Builder
public record AddressTemplateFieldDto(
        Long id,
        String fieldKey,
        String fieldLabelKey,
        String fieldType,
        String masterDataSource,
        String parentFieldKey
) {
    public static AddressTemplateFieldDto from(AddressTemplateField field) {
        return AddressTemplateFieldDto.builder()
                .id(field.getId())
                .fieldKey(field.getFieldKey())
                .fieldLabelKey(field.getFieldLabelKey())
                .fieldType(field.getFieldType().name())
                .masterDataSource(field.getMasterDataSource().name())
                .parentFieldKey(field.getParentFieldKey())
                .build();
    }
}
