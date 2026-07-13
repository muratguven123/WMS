package com.wms.localization.dto.address;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

/**
 * Adres oluşturma / güncelleme isteği DTO'su.
 *
 * <p>{@code addressDetails} içindeki key'ler {@code AddressTemplateField.fieldKey}
 * ile eşleşmelidir. Dinamik alan validasyonu sunucu taraflı
 * {@code AddressValidationService} tarafından yapılır.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDto {

    @NotNull(message = "countryId zorunludur")
    private Long countryId;

    private String city;
    private String state;
    private String zipCode;

    /**
     * Ülkeye özgü dinamik alanlar.
     * Key   → AddressTemplateField.fieldKey  (örn: "district")
     * Value → kullanıcı girişi              (örn: "Kadıköy")
     */
    private Map<String, Object> addressDetails;
}
