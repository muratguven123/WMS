package com.wms.localization.service;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.entity.CountryFormatConfig;
import com.wms.localization.entity.LocationFormatOverride;
import com.wms.localization.integration.LocationCountryResolver;
import com.wms.localization.repository.CountryFormatConfigRepository;
import com.wms.localization.repository.LocationFormatOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Frontend / Mobil istemcilere aktif depo format konfigürasyonunu sunan servis.
 *
 * <h3>Hiyerarşik Çözümleme Zinciri</h3>
 * <ol>
 *   <li><b>Depo bazlı override</b> ({@link LocationFormatOverride}) — en yüksek öncelik.</li>
 *   <li><b>Ülke bazlı varsayılan</b> ({@link CountryFormatConfig}) —
 *       {@link LocationCountryResolver} aracılığıyla {@code countryId} bulunursa devreye girer.</li>
 *   <li><b>Uygulama sabit varsayılanları</b> — hiçbir kayıt bulunamazsa güvenli dönüş.</li>
 * </ol>
 *
 * <p>Çözümleme asla exception fırlatmaz; en kötü durumda ISO standart
 * varsayılanları döner.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormatConfigService {

    // Uygulama sabit varsayılanları — ISO 8601 + ABD sayı formatı
    private static final String DEFAULT_DATE_FORMAT        = "yyyy-MM-dd";
    private static final String DEFAULT_TIME_FORMAT        = "HH:mm";
    private static final String DEFAULT_DECIMAL_SEPARATOR  = ".";
    private static final String DEFAULT_THOUSAND_SEPARATOR = ",";

    private final LocationFormatOverrideRepository locationFormatOverrideRepository;
    private final CountryFormatConfigRepository    countryFormatConfigRepository;
    private final LocationCountryResolver          locationCountryResolver;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Aktif lokasyon için format konfigürasyonunu çözümler ve döner.
     *
     * <p>Çağıran (Controller), {@code locationId}'yi {@code TenantContextHolder}
     * üzerinden alıp bu metoda iletir. Servis, depo → ülke ilişkisini
     * {@link LocationCountryResolver} aracılığıyla çözer.</p>
     *
     * @param locationId TenantContextHolder'dan alınan aktif depo Long'si
     * @return Çözümlenmiş format konfigürasyonu; asla null değil
     */
    public ActiveFormatResponse resolveActiveFormat(Long locationId) {
        if (locationId == null) {
            log.debug("locationId yok — uygulama varsayılanları kullanılıyor");
            return applicationDefaultResponse();
        }

        // 1. Depo bazlı geçersiz kılma
        Optional<LocationFormatOverride> overrideOpt =
                locationFormatOverrideRepository.findByLocationId(locationId);

        if (overrideOpt.isPresent() && isFullyDefined(overrideOpt.get())) {
            LocationFormatOverride ov = overrideOpt.get();
            log.debug("Format çözümlendi — kaynak: LocationFormatOverride. locationId={}", locationId);
            return ActiveFormatResponse.of(
                    ov.getDateFormat(),
                    ov.getTimeFormat(),
                    ov.getDecimalSeparator(),
                    ov.getThousandSeparator()
            );
        }

        // 2. Ülke bazlı varsayılan
        Optional<Long> countryIdOpt = locationCountryResolver.resolveCountryId(locationId);

        if (countryIdOpt.isPresent()) {
            Optional<CountryFormatConfig> countryConfigOpt =
                    countryFormatConfigRepository.findByCountryId(countryIdOpt.get());

            if (countryConfigOpt.isPresent()) {
                CountryFormatConfig cc = countryConfigOpt.get();

                // Kısmi override varsa ülkeden tamamla; yoksa doğrudan ülke config'i kullan
                if (overrideOpt.isPresent()) {
                    log.debug("Format çözümlendi — kaynak: kısmi override + CountryFormatConfig. locationId={}", locationId);
                    return mergeOverrideWithCountry(overrideOpt.get(), cc);
                }

                log.debug("Format çözümlendi — kaynak: CountryFormatConfig. locationId={}, countryId={}",
                        locationId, countryIdOpt.get());
                return ActiveFormatResponse.of(
                        cc.getDateFormat(),
                        cc.getTimeFormat(),
                        cc.getDecimalSeparator(),
                        cc.getThousandSeparator()
                );
            }
        }

        // 3. Her şey başarısız — uygulama varsayılanları
        log.warn("Format kaydı bulunamadı, uygulama varsayılanları kullanılıyor. locationId={}", locationId);

        // Kısmi override varsa eksik alanları uygulama varsayılanıyla tamamla
        if (overrideOpt.isPresent()) {
            return mergeOverrideWithDefaults(overrideOpt.get());
        }

        return applicationDefaultResponse();
    }

    // =========================================================================
    // Yardımcı metotlar
    // =========================================================================

    private boolean isFullyDefined(LocationFormatOverride ov) {
        return ov.getDateFormat()        != null
            && ov.getTimeFormat()        != null
            && ov.getDecimalSeparator()  != null
            && ov.getThousandSeparator() != null;
    }

    private ActiveFormatResponse mergeOverrideWithCountry(
            LocationFormatOverride ov, CountryFormatConfig cc) {
        return ActiveFormatResponse.of(
                coalesce(ov.getDateFormat(),        cc.getDateFormat()),
                coalesce(ov.getTimeFormat(),        cc.getTimeFormat()),
                coalesce(ov.getDecimalSeparator(),  cc.getDecimalSeparator()),
                coalesce(ov.getThousandSeparator(), cc.getThousandSeparator())
        );
    }

    private ActiveFormatResponse mergeOverrideWithDefaults(LocationFormatOverride ov) {
        return ActiveFormatResponse.of(
                coalesce(ov.getDateFormat(),        DEFAULT_DATE_FORMAT),
                coalesce(ov.getTimeFormat(),        DEFAULT_TIME_FORMAT),
                coalesce(ov.getDecimalSeparator(),  DEFAULT_DECIMAL_SEPARATOR),
                coalesce(ov.getThousandSeparator(), DEFAULT_THOUSAND_SEPARATOR)
        );
    }

    private ActiveFormatResponse applicationDefaultResponse() {
        return ActiveFormatResponse.of(
                DEFAULT_DATE_FORMAT,
                DEFAULT_TIME_FORMAT,
                DEFAULT_DECIMAL_SEPARATOR,
                DEFAULT_THOUSAND_SEPARATOR
        );
    }

    private static String coalesce(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
