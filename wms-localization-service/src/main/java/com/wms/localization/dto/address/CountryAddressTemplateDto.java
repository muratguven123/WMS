package com.wms.localization.dto.address;

import com.wms.localization.domain.address.AddressTemplateField;
import com.wms.localization.domain.address.CountryAddressTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Arayüze dönülecek şablon alanı verisi.
 *
 * <p>UI, bu DTO'daki {@code fieldType}/{@code masterDataSource}/{@code parentFieldKey}
 * bilgisine bakarak bir alanın serbest metin mi yoksa core-service master-data
 * cascade'ine bağlı bir seçim kutusu mu olduğuna karar verir — {@code fieldKey}
 * ismine göre hardcode bir switch yazmaz.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryAddressTemplateDto {

    /** Şablon satırı PK — admin CRUD için. */
    private Long id;

    /** Katalog alan PK — admin CRUD için. */
    private Long fieldId;

    private String fieldKey;
    private String fieldLabelKey;
    private boolean mandatory;
    private int sequence;
    private String validationRegex;
    private String errorMessageKey;

    /** {@code AddressTemplateField.FieldType} — "TEXT" | "MASTER_SELECT" */
    private String fieldType;

    /** {@code AddressTemplateField.MasterDataSource} — "NONE" | "DISTRICT" | "NEIGHBORHOOD" */
    private String masterDataSource;

    /** Bu alanın bağımlı olduğu üst alan (bkz. {@link AddressTemplateField#getParentFieldKey()}) */
    private String parentFieldKey;

    public static CountryAddressTemplateDto from(CountryAddressTemplate template) {
        AddressTemplateField field = template.getAddressTemplateField();
        return CountryAddressTemplateDto.builder()
                .id(template.getId())
                .fieldId(field.getId())
                .fieldKey(field.getFieldKey())
                .fieldLabelKey(field.getFieldLabelKey())
                .mandatory(template.isMandatory())
                .sequence(template.getSequence())
                .validationRegex(template.getValidationRegex())
                .errorMessageKey(template.getErrorMessageKey())
                .fieldType(field.getFieldType().name())
                .masterDataSource(field.getMasterDataSource().name())
                .parentFieldKey(field.getParentFieldKey())
                .build();
    }
}
