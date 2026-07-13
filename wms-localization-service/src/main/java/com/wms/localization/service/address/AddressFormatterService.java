package com.wms.localization.service.address;

import com.wms.localization.service.address.formatter.AddressFormatterStrategy;
import com.wms.localization.service.address.formatter.CountryIsoCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ülkeye özgü adres biçimlendirme orkestrasyonu.
 *
 * <p>Spring context'teki tüm {@link AddressFormatterStrategy} implementasyonlarını
 * constructor'da otomatik enjekte eder ve ISO kodu üzerinden dispatch yapar.
 * Yeni bir ülke eklemek için sadece {@code AddressFormatterStrategy} implement eden
 * bir {@code @Component} yazmak yeterlidir — bu sınıfa dokunulmasına gerek yoktur.</p>
 *
 * <h3>Fallback</h3>
 * <p>Bilinmeyen ülke ISO kodu için, mevcut tüm alanları virgülle birleştiren
 * genel amaçlı fallback kullanılır.</p>
 */
@Slf4j
@Service
public class AddressFormatterService {

    private final CountryIsoCodeResolver isoCodeResolver;
    private final Map<String, AddressFormatterStrategy> strategyMap;

    public AddressFormatterService(
            CountryIsoCodeResolver isoCodeResolver,
            List<AddressFormatterStrategy> strategies
    ) {
        this.isoCodeResolver = isoCodeResolver;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AddressFormatterStrategy::isoCode,
                        Function.identity()
                ));
        log.info("AddressFormatterService initialized with strategies: {}", strategyMap.keySet());
    }

    /**
     * Belirtilen ülke kurallarına göre biçimlendirilmiş adres metni üretir.
     *
     * @param countryId      core servisteki Country Long'si
     * @param addressDetails JSONB dinamik alan haritası
     * @param city           şehir
     * @param state          eyalet / il
     * @param zipCode        posta kodu
     * @return temizlenmiş tek satırlık adres; hiç veri yoksa boş string
     */
    public String generateFormattedAddress(
            Long countryId,
            Map<String, Object> addressDetails,
            String city,
            String state,
            String zipCode
    ) {
        String isoCode = isoCodeResolver.resolve(countryId);
        AddressFormatterStrategy strategy = strategyMap.get(isoCode);

        if (strategy == null) {
            log.warn("No formatter strategy found for isoCode='{}', using fallback", isoCode);
            return fallbackFormat(addressDetails, city, state, zipCode);
        }

        String formatted = strategy.format(addressDetails, city, state, zipCode);
        log.debug("Formatted address for countryId={} ({}): '{}'", countryId, isoCode, formatted);
        return formatted;
    }

    // -------------------------------------------------------------------------
    // Fallback: bilinmeyen ülke için generic birleştirme
    // -------------------------------------------------------------------------

    private String fallbackFormat(
            Map<String, Object> details,
            String city,
            String state,
            String zipCode
    ) {
        var parts = new java.util.ArrayList<String>();

        if (details != null) {
            details.values().stream()
                    .filter(v -> v != null && !v.toString().isBlank())
                    .map(Object::toString)
                    .forEach(parts::add);
        }
        if (city    != null && !city.isBlank())    parts.add(city.trim());
        if (state   != null && !state.isBlank())   parts.add(state.trim());
        if (zipCode != null && !zipCode.isBlank()) parts.add(zipCode.trim());

        return String.join(", ", parts);
    }
}
