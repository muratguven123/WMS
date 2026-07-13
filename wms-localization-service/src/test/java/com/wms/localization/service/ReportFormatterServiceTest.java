package com.wms.localization.service;

import com.wms.localization.entity.CountryFormatConfig;
import com.wms.localization.entity.LocationFormatOverride;
import com.wms.localization.integration.LocationCountryResolver;
import com.wms.localization.repository.CountryFormatConfigRepository;
import com.wms.localization.repository.LocationFormatOverrideRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ReportFormatterService} birim testleri.
 *
 * <p>Her senaryo bağımsız mock'larla çalışır; veritabanı veya Spring context'i
 * gerektirmez. Test sınıfları iç @Nested gruplarıyla konuya göre ayrılmıştır.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportFormatterService")
class ReportFormatterServiceTest {

    @Mock
    private LocationFormatOverrideRepository locationFormatOverrideRepository;

    @Mock
    private CountryFormatConfigRepository countryFormatConfigRepository;

    @Mock
    private LocationCountryResolver locationCountryResolver;

    @InjectMocks
    private ReportFormatterService service;

    // ---------------------------------------------------------------------------
    // Test sabitleri
    // ---------------------------------------------------------------------------

    private static final Long LOCATION_ID = 1L;
    private static final Long COUNTRY_ID  = 102L;

    /** 2024-03-15 14:30:00 UTC */
    private static final Instant TEST_INSTANT = Instant.parse("2024-03-15T14:30:00Z");

    // ---------------------------------------------------------------------------
    // Yardımcı builder metotları
    // ---------------------------------------------------------------------------

    private LocationFormatOverride fullTrOverride() {
        return LocationFormatOverride.builder()
                .id(1L)
                .locationId(LOCATION_ID)
                .dateFormat("dd.MM.yyyy")
                .timeFormat("HH:mm")
                .decimalSeparator(",")
                .thousandSeparator(".")
                .build();
    }

    private LocationFormatOverride fullUsOverride() {
        return LocationFormatOverride.builder()
                .id(1L)
                .locationId(LOCATION_ID)
                .dateFormat("MM/dd/yyyy")
                .timeFormat("hh:mm a")
                .decimalSeparator(".")
                .thousandSeparator(",")
                .build();
    }

    private CountryFormatConfig trCountryConfig() {
        return CountryFormatConfig.builder()
                .id(1L)
                .countryId(COUNTRY_ID)
                .dateFormat("dd.MM.yyyy")
                .timeFormat("HH:mm")
                .decimalSeparator(",")
                .thousandSeparator(".")
                .build();
    }

    // ===========================================================================
    // formatDateTime testleri
    // ===========================================================================

    @Nested
    @DisplayName("formatDateTime()")
    class FormatDateTimeTests {

        @Test
        @DisplayName("TR lokasyonu — Europe/Istanbul timezone — dd.MM.yyyy HH:mm")
        void formatDateTime_trLocale_istanbul() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatDateTime(TEST_INSTANT, LOCATION_ID, "Europe/Istanbul");

            // 2024-03-15 14:30 UTC → Europe/Istanbul (UTC+3) → 2024-03-15 17:30
            assertThat(result).isEqualTo("15.03.2024 17:30");
        }

        @Test
        @DisplayName("US lokasyonu — America/New_York timezone — MM/dd/yyyy hh:mm a")
        void formatDateTime_usLocale_newYork() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullUsOverride()));

            String result = service.formatDateTime(TEST_INSTANT, LOCATION_ID, "America/New_York");

            // 2024-03-15 14:30 UTC → New York (EDT = UTC-4 in March DST) → 10:30 AM
            assertThat(result).isEqualTo("03/15/2024 10:30 AM");
        }

        @Test
        @DisplayName("Override yok, ülke config var → ülke formatı kullanılır")
        void formatDateTime_noOverride_usesCountryConfig() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.empty());
            when(locationCountryResolver.resolveCountryId(LOCATION_ID))
                    .thenReturn(Optional.of(COUNTRY_ID));
            when(countryFormatConfigRepository.findByCountryId(COUNTRY_ID))
                    .thenReturn(Optional.of(trCountryConfig()));

            String result = service.formatDateTime(TEST_INSTANT, LOCATION_ID, "Europe/Istanbul");

            assertThat(result).isEqualTo("15.03.2024 17:30");
        }

        @Test
        @DisplayName("UTC instant UTC timezone — uygulama varsayılanı (kayıt yok)")
        void formatDateTime_noOverride_usesApplicationDefault() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.empty());
            when(locationCountryResolver.resolveCountryId(LOCATION_ID))
                    .thenReturn(Optional.empty());

            String result = service.formatDateTime(TEST_INSTANT, LOCATION_ID, "UTC");

            // Varsayılan: yyyy-MM-dd HH:mm
            assertThat(result).isEqualTo("2024-03-15 14:30");
        }

        @Test
        @DisplayName("null Instant — boş string döner, istisna fırlatmaz")
        void formatDateTime_nullInstant_returnsEmpty() {
            String result = service.formatDateTime(null, LOCATION_ID, "UTC");

            assertThat(result).isEmpty();
            verifyNoInteractions(locationFormatOverrideRepository);
        }

        @Test
        @DisplayName("Geçersiz timezone — UTC'ye fall-back, formatlar")
        void formatDateTime_invalidTimezone_fallsBackToUtc() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatDateTime(TEST_INSTANT, LOCATION_ID, "InvalidZone/NotReal");

            // UTC'ye fall-back → 14:30
            assertThat(result).isEqualTo("15.03.2024 14:30");
        }
    }

    // ===========================================================================
    // formatNumber testleri
    // ===========================================================================

    @Nested
    @DisplayName("formatNumber()")
    class FormatNumberTests {

        @Test
        @DisplayName("TR lokasyonu — 1250.50 → '1.250,50'")
        void formatNumber_trLocale_thousandDotDecimalComma() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatNumber(new BigDecimal("1250.50"), LOCATION_ID);

            assertThat(result).isEqualTo("1.250,50");
        }

        @Test
        @DisplayName("US lokasyonu — 1250.50 → '1,250.50'")
        void formatNumber_usLocale_thousandCommaDecimalDot() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullUsOverride()));

            String result = service.formatNumber(new BigDecimal("1250.50"), LOCATION_ID);

            assertThat(result).isEqualTo("1,250.50");
        }

        @Test
        @DisplayName("Büyük tutar — 1_000_000.99 TR formatı → '1.000.000,99'")
        void formatNumber_largeTrAmount() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatNumber(new BigDecimal("1000000.99"), LOCATION_ID);

            assertThat(result).isEqualTo("1.000.000,99");
        }

        @Test
        @DisplayName("Sıfır değer — '0' döner")
        void formatNumber_zero() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatNumber(BigDecimal.ZERO, LOCATION_ID);

            assertThat(result).isEqualTo("0");
        }

        @Test
        @DisplayName("null amount — '0' döner, istisna fırlatmaz")
        void formatNumber_nullAmount_returnsZero() {
            String result = service.formatNumber(null, LOCATION_ID);

            assertThat(result).isEqualTo("0");
            verifyNoInteractions(locationFormatOverrideRepository);
        }

        @Test
        @DisplayName("Kayıt yok — uygulama varsayılanı (. ondalık , binlik) kullanılır")
        void formatNumber_noOverride_usesApplicationDefault() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.empty());
            when(locationCountryResolver.resolveCountryId(LOCATION_ID))
                    .thenReturn(Optional.empty());

            String result = service.formatNumber(new BigDecimal("1250.50"), LOCATION_ID);

            // Varsayılan: "." ondalık, "," binlik, 2 ondalık basamak
            assertThat(result).isEqualTo("1,250.50");
        }

        @Test
        @DisplayName("Tam sayı tutar — ondalık kısım gösterilmez")
        void formatNumber_wholeNumber_noDecimalShown() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            String result = service.formatNumber(new BigDecimal("5000"), LOCATION_ID);

            assertThat(result).isEqualTo("5.000");
        }
    }

    // ===========================================================================
    // resolveFormat(locationId, countryId) testleri
    // ===========================================================================

    @Nested
    @DisplayName("resolveFormat(locationId, countryId) — hiyerarşik çözümleme")
    class ResolveFormatTests {

        @Test
        @DisplayName("Tam override varsa ülkeye gidilmez")
        void resolveFormat_fullOverride_doesNotQueryCountry() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(fullTrOverride()));

            ReportFormatterService.ResolvedFormat fmt =
                    service.resolveFormat(LOCATION_ID, COUNTRY_ID);

            assertThat(fmt.dateFormat()).isEqualTo("dd.MM.yyyy");
            verifyNoInteractions(countryFormatConfigRepository);
        }

        @Test
        @DisplayName("Override yok → ülke config'i kullanılır")
        void resolveFormat_noOverride_usesCountryConfig() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.empty());
            when(countryFormatConfigRepository.findByCountryId(COUNTRY_ID))
                    .thenReturn(Optional.of(trCountryConfig()));

            ReportFormatterService.ResolvedFormat fmt =
                    service.resolveFormat(LOCATION_ID, COUNTRY_ID);

            assertThat(fmt.dateFormat()).isEqualTo("dd.MM.yyyy");
            assertThat(fmt.decimalSeparator()).isEqualTo(",");
            assertThat(fmt.thousandSeparator()).isEqualTo(".");
        }

        @Test
        @DisplayName("Kısmi override — eksik alanlar ülkeden tamamlanır")
        void resolveFormat_partialOverride_mergesWithCountry() {
            // Sadece dateFormat dolu, diğerleri null
            LocationFormatOverride partial = LocationFormatOverride.builder()
                    .id(1L)
                    .locationId(LOCATION_ID)
                    .dateFormat("dd/MM/yyyy")   // Sadece bu dolu
                    .timeFormat(null)
                    .decimalSeparator(null)
                    .thousandSeparator(null)
                    .build();

            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.of(partial));
            when(countryFormatConfigRepository.findByCountryId(COUNTRY_ID))
                    .thenReturn(Optional.of(trCountryConfig()));

            ReportFormatterService.ResolvedFormat fmt =
                    service.resolveFormat(LOCATION_ID, COUNTRY_ID);

            assertThat(fmt.dateFormat()).isEqualTo("dd/MM/yyyy");   // override'dan
            assertThat(fmt.timeFormat()).isEqualTo("HH:mm");        // ülkeden
            assertThat(fmt.decimalSeparator()).isEqualTo(",");      // ülkeden
        }

        @Test
        @DisplayName("Override yok, ülke config yok → uygulama varsayılanları")
        void resolveFormat_noOverride_noCountry_usesDefaults() {
            when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                    .thenReturn(Optional.empty());
            when(countryFormatConfigRepository.findByCountryId(COUNTRY_ID))
                    .thenReturn(Optional.empty());

            ReportFormatterService.ResolvedFormat fmt =
                    service.resolveFormat(LOCATION_ID, COUNTRY_ID);

            assertThat(fmt.dateFormat()).isEqualTo("yyyy-MM-dd");
            assertThat(fmt.decimalSeparator()).isEqualTo(".");
        }
    }
}
