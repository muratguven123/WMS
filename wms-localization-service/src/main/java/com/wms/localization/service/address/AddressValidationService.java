package com.wms.localization.service.address;

import com.wms.localization.domain.address.CountryAddressTemplate;
import com.wms.localization.dto.address.AddressDto;
import com.wms.localization.exception.address.AddressValidationException;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ülkeye özgü adres alanlarını {@link CountryAddressTemplate} kurallarına göre doğrular.
 *
 * <h3>Doğrulama Algoritması</h3>
 * <ol>
 *   <li>İlgili ülkenin şablon kurallarını sıralı olarak DB'den çek.</li>
 *   <li>Her kural için:
 *     <ul>
 *       <li><b>Zorunluluk kontrolü:</b> {@code isMandatory=true} ise alan mevcut ve dolu mu?</li>
 *       <li><b>Regex kontrolü:</b> {@code validationRegex != null} ise değer pattern ile eşleşiyor mu?</li>
 *     </ul>
 *   </li>
 *   <li>Tüm ihlalleri topla; varsa {@link AddressValidationException} fırlat (fail-all semantiği).</li>
 * </ol>
 *
 * <p>Regex {@link Pattern} nesneleri method-local compile edilir.
 * Performans kritik senaryolarda bu nesneler bir {@code ConcurrentHashMap} cache'inde tutulabilir.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressValidationService {

    private final CountryAddressTemplateRepository templateRepository;

    /**
     * {@code addressDto} içindeki {@code addressDetails} haritasını,
     * {@code countryId}'ye bağlı {@link CountryAddressTemplate} kurallarına göre doğrular.
     *
     * @param addressDto doğrulanacak adres DTO'su
     * @throws AddressValidationException bir veya daha fazla kural ihlali varsa
     */
    @Transactional(readOnly = true)
    public void validateAddress(AddressDto addressDto) {
        List<CountryAddressTemplate> templates =
                templateRepository.findByCountryIdOrderBySequenceAsc(addressDto.getCountryId());

        if (templates.isEmpty()) {
            log.debug("No address templates found for countryId={}, skipping validation",
                    addressDto.getCountryId());
            return;
        }

        List<String> violations = new ArrayList<>();

        for (CountryAddressTemplate template : templates) {
            String fieldKey = template.getAddressTemplateField().getFieldKey();

            // Değeri String'e normalize et (standart sütunlar + JSONB)
            String fieldValue = extractFieldValue(addressDto, fieldKey);

            // ── 1. Zorunluluk kontrolü ─────────────────────────────────────
            if (template.isMandatory() && !StringUtils.hasText(fieldValue)) {
                String errorKey = resolveErrorKey(
                        template.getErrorMessageKey(),
                        "validation." + fieldKey + ".required"
                );
                log.debug("Mandatory field missing: fieldKey={}, errorKey={}", fieldKey, errorKey);
                violations.add(errorKey);
                continue; // Değer yoksa regex kontrolüne gerek yok
            }

            // ── 2. Regex kontrolü ─────────────────────────────────────────
            if (StringUtils.hasText(fieldValue) && StringUtils.hasText(template.getValidationRegex())) {
                boolean matches = Pattern
                        .compile(template.getValidationRegex())
                        .matcher(fieldValue)
                        .matches();

                if (!matches) {
                    String errorKey = resolveErrorKey(
                            template.getErrorMessageKey(),
                            "validation." + fieldKey + ".invalid"
                    );
                    log.debug("Regex mismatch: fieldKey={}, value={}, regex={}, errorKey={}",
                            fieldKey, fieldValue, template.getValidationRegex(), errorKey);
                    violations.add(errorKey);
                }
            }
        }

        if (!violations.isEmpty()) {
            throw AddressValidationException.ofAll(violations);
        }
    }

    // -------------------------------------------------------------------------
    // Yardımcı metodlar
    // -------------------------------------------------------------------------

    /**
     * Şablondaki fieldKey için değeri DTO'dan okur.
     * {@code city}, {@code state}, {@code zip_code} hem üst seviye sütunlardan hem JSONB'den gelebilir.
     */
    private String extractFieldValue(AddressDto dto, String fieldKey) {
        return switch (fieldKey) {
            case "city"     -> firstNonBlank(dto.getCity(), extractStringValue(dto.getAddressDetails(), fieldKey));
            case "state"    -> firstNonBlank(dto.getState(), extractStringValue(dto.getAddressDetails(), fieldKey));
            case "zip_code" -> firstNonBlank(dto.getZipCode(), extractStringValue(dto.getAddressDetails(), fieldKey));
            default         -> extractStringValue(dto.getAddressDetails(), fieldKey);
        };
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    /**
     * {@code addressDetails} haritasından fieldKey'e karşılık gelen değeri güvenli biçimde
     * {@code String}'e çevirir. Harita null veya key mevcut değilse {@code null} döner.
     */
    private String extractStringValue(Map<String, Object> details, String fieldKey) {
        if (details == null) {
            return null;
        }
        Object value = details.get(fieldKey);
        return (value != null) ? value.toString() : null;
    }

    /**
     * Şablonda {@code errorMessageKey} tanımlıysa onu döner; aksi hâlde {@code fallback} kullanılır.
     */
    private String resolveErrorKey(String templateErrorKey, String fallback) {
        return StringUtils.hasText(templateErrorKey) ? templateErrorKey : fallback;
    }
}
