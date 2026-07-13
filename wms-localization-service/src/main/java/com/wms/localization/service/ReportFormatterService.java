package com.wms.localization.service;

import com.wms.localization.entity.CountryFormatConfig;
import com.wms.localization.entity.LocationFormatOverride;
import com.wms.localization.integration.LocationCountryResolver;
import com.wms.localization.repository.CountryFormatConfigRepository;
import com.wms.localization.repository.LocationFormatOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Sunucu tarafı belge ve rapor biçimlendirme servisi.
 *
 * <p>UTC tabanlı {@link Instant} değerlerini ve büyük sayısal tutarları
 * ({@link BigDecimal}), ilgili deponun (Location) yerel format kurallarına
 * göre insan tarafından okunabilir string'e dönüştürür.</p>
 *
 * <h3>Hiyerarşik Format Çözümleme</h3>
 * <ol>
 *   <li>Depo (Location) bazlı {@link LocationFormatOverride} kaydına bak.</li>
 *   <li>Kayıt yoksa veya ilgili alan null ise, ülkenin
 *       {@link CountryFormatConfig} varsayılanını kullan.</li>
 *   <li>Her ikisi de bulunamazsa uygulama varsayılanını ({@code ISO_LOCAL_DATE_TIME}
 *       ve {@code #,##0.##}) uygula — asla istisna fırlatma.</li>
 * </ol>
 *
 * <h3>Kullanım Örneği</h3>
 * <pre>{@code
 * // PDF raporu üretimi sırasında
 * String date   = formatter.formatDateTime(record.getCreatedAt(), locationId, "Europe/Istanbul");
 * String amount = formatter.formatNumber(record.getTotalAmount(), locationId);
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportFormatterService {

    // Hiçbir format kaydı bulunamazsa kullanılan güvenli varsayılanlar
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
     * UTC {@link Instant}'ı hedef timezone'a çevirerek, deponun tarih+saat
     * format deseniyle biçimlendirilmiş string döner.
     *
     * <p>Format deseni: {@code dateFormat + " " + timeFormat}
     * (örn: {@code "dd.MM.yyyy HH:mm"})</p>
     *
     * @param utcInstant      UTC zaman damgası (null ise boş string döner)
     * @param locationId      wms-core-service Location Long
     * @param targetTimezone  IANA timezone kimliği (örn: "Europe/Istanbul", "UTC")
     * @return Biçimlendirilmiş tarih-saat string'i
     */
    public String formatDateTime(Instant utcInstant, Long locationId, String targetTimezone) {
        if (utcInstant == null) {
            log.warn("formatDateTime çağrıldı ancak utcInstant null — boş string döndürülüyor. locationId={}", locationId);
            return "";
        }

        ResolvedFormat fmt = resolveFormat(locationId);
        String pattern = fmt.dateFormat() + " " + fmt.timeFormat();

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(targetTimezone);
        } catch (Exception e) {
            log.warn("Geçersiz timezone '{}', UTC kullanılıyor. locationId={}", targetTimezone, locationId);
            zoneId = ZoneId.of("UTC");
        }

        DateTimeFormatter formatter = pattern.contains("a")
                ? DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zoneId)
                : DateTimeFormatter.ofPattern(pattern).withZone(zoneId);

        return formatter.format(utcInstant);
    }

    /**
     * {@link BigDecimal} tutarını, deponun ondalık ve binlik ayraç kurallarına
     * göre biçimlendirilmiş string döner.
     *
     * <p>Örnekler:
     * <ul>
     *   <li>TR: {@code 1250.50} → {@code "1.250,50"}</li>
     *   <li>US: {@code 1250.50} → {@code "1,250.50"}</li>
     * </ul>
     * </p>
     *
     * @param amount     Biçimlendirilecek tutar (null ise "0" döner)
     * @param locationId wms-core-service Location Long
     * @return Biçimlendirilmiş sayı string'i
     */
    public String formatNumber(BigDecimal amount, Long locationId) {
        if (amount == null) {
            log.warn("formatNumber çağrıldı ancak amount null — '0' döndürülüyor. locationId={}", locationId);
            return "0";
        }

        ResolvedFormat fmt = resolveFormat(locationId);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(fmt.decimalSeparator().charAt(0));
        symbols.setGroupingSeparator(fmt.thousandSeparator().charAt(0));

        int fractionDigits = amount.stripTrailingZeros().scale() > 0 ? 2 : 0;
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.##", symbols);
        decimalFormat.setMinimumFractionDigits(fractionDigits);
        decimalFormat.setMaximumFractionDigits(fractionDigits);
        decimalFormat.setParseBigDecimal(true);

        return decimalFormat.format(amount);
    }

    // =========================================================================
    // Hiyerarşik Format Çözümleme
    // =========================================================================

    /**
     * locationId için format değerlerini şu öncelik sırasıyla çözümler:
     * <ol>
     *   <li>{@link LocationFormatOverride} — depo özgü</li>
     *   <li>{@link CountryFormatConfig}    — ülke varsayılanı (countryId bilinmiyorsa atlanır)</li>
     *   <li>Uygulama sabit varsayılanları</li>
     * </ol>
     *
     * <p>Bu metot hiçbir zaman istisna fırlatmaz; en kötü durumda
     * uygulama varsayılanlarını döner.</p>
     */
    private ResolvedFormat resolveFormat(Long locationId) {
        Optional<LocationFormatOverride> overrideOpt =
                locationFormatOverrideRepository.findByLocationId(locationId);

        if (overrideOpt.isPresent() && isFullyDefined(overrideOpt.get())) {
            LocationFormatOverride ov = overrideOpt.get();
            return new ResolvedFormat(
                    ov.getDateFormat(),
                    ov.getTimeFormat(),
                    ov.getDecimalSeparator(),
                    ov.getThousandSeparator()
            );
        }

        Optional<Long> countryIdOpt = locationCountryResolver.resolveCountryId(locationId);
        if (countryIdOpt.isPresent()) {
            Optional<CountryFormatConfig> countryOpt =
                    countryFormatConfigRepository.findByCountryId(countryIdOpt.get());

            if (countryOpt.isPresent()) {
                CountryFormatConfig cc = countryOpt.get();
                if (overrideOpt.isPresent()) {
                    return mergeOverrideWithCountry(overrideOpt.get(), cc);
                }
                return new ResolvedFormat(
                        cc.getDateFormat(),
                        cc.getTimeFormat(),
                        cc.getDecimalSeparator(),
                        cc.getThousandSeparator()
                );
            }
        }

        if (overrideOpt.isPresent()) {
            log.debug("LocationFormatOverride kısmi — uygulama varsayılanlarına fall-back. locationId={}", locationId);
            return mergeWithDefaults(overrideOpt.get());
        }

        log.debug("Format kaydı bulunamadı — uygulama varsayılanları. locationId={}", locationId);
        return applicationDefaults();
    }

    /**
     * CountryFormatConfig ile ülke çözümlemesini doğrudan countryId üzerinden yapar.
     * Ülke-depo ilişkisini bilen çağıranlar (örn: gelecekteki LocationService entegrasyonu)
     * bu metodu kullanabilir.
     *
     * @param locationId wms-core-service Location Long
     * @param countryId  wms-core-service Country Long
     */
    public ResolvedFormat resolveFormat(Long locationId, Long countryId) {
        Optional<LocationFormatOverride> overrideOpt =
                locationFormatOverrideRepository.findByLocationId(locationId);

        if (overrideOpt.isPresent() && isFullyDefined(overrideOpt.get())) {
            LocationFormatOverride ov = overrideOpt.get();
            return new ResolvedFormat(
                    ov.getDateFormat(),
                    ov.getTimeFormat(),
                    ov.getDecimalSeparator(),
                    ov.getThousandSeparator()
            );
        }

        // Ülke varsayılanına fall-back
        Optional<CountryFormatConfig> countryOpt =
                countryFormatConfigRepository.findByCountryId(countryId);

        if (countryOpt.isPresent()) {
            CountryFormatConfig cc = countryOpt.get();

            if (overrideOpt.isPresent()) {
                // Kısmi override — ülkeden eksik alanları tamamla
                return mergeOverrideWithCountry(overrideOpt.get(), cc);
            }

            return new ResolvedFormat(
                    cc.getDateFormat(),
                    cc.getTimeFormat(),
                    cc.getDecimalSeparator(),
                    cc.getThousandSeparator()
            );
        }

        // Her şey başarısız — uygulama varsayılanı
        log.warn("Ne depo ne de ülke format kaydı bulunamadı. locationId={}, countryId={}", locationId, countryId);
        return overrideOpt.map(this::mergeWithDefaults).orElse(applicationDefaults());
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

    private ResolvedFormat mergeWithDefaults(LocationFormatOverride ov) {
        return new ResolvedFormat(
                coalesce(ov.getDateFormat(),        DEFAULT_DATE_FORMAT),
                coalesce(ov.getTimeFormat(),        DEFAULT_TIME_FORMAT),
                coalesce(ov.getDecimalSeparator(),  DEFAULT_DECIMAL_SEPARATOR),
                coalesce(ov.getThousandSeparator(), DEFAULT_THOUSAND_SEPARATOR)
        );
    }

    private ResolvedFormat mergeOverrideWithCountry(LocationFormatOverride ov, CountryFormatConfig cc) {
        return new ResolvedFormat(
                coalesce(ov.getDateFormat(),        cc.getDateFormat()),
                coalesce(ov.getTimeFormat(),        cc.getTimeFormat()),
                coalesce(ov.getDecimalSeparator(),  cc.getDecimalSeparator()),
                coalesce(ov.getThousandSeparator(), cc.getThousandSeparator())
        );
    }

    private ResolvedFormat applicationDefaults() {
        return new ResolvedFormat(
                DEFAULT_DATE_FORMAT,
                DEFAULT_TIME_FORMAT,
                DEFAULT_DECIMAL_SEPARATOR,
                DEFAULT_THOUSAND_SEPARATOR
        );
    }

    private static String coalesce(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    // =========================================================================
    // Value Object
    // =========================================================================

    /**
     * Çözümlenmiş format değerlerini taşıyan immutable record.
     * Hem iç kullanım hem de dış entegrasyon (cross-service) için public.
     */
    public record ResolvedFormat(
            String dateFormat,
            String timeFormat,
            String decimalSeparator,
            String thousandSeparator
    ) {}
}
