package com.wms.finance.service;

import com.wms.finance.dto.ConversionResultDto;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CurrencyConversionService")
class CurrencyConversionServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CurrencyConversionService conversionService;

    private Currency tryCurrency;
    private Currency jpyCurrency;

    @BeforeEach
    void setUp() {
        tryCurrency = Currency.builder().code("TRY").decimalPlaces(2).active(true).build();
        jpyCurrency = Currency.builder().code("JPY").decimalPlaces(0).active(true).build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        stubNoManualRates();
    }

    private void stubNoManualRates() {
        when(exchangeRateRepository.findExactRate(anyString(), anyString(), any(), any(), eq(RateSource.MANUAL)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                anyString(), anyString(), anyString(), eq("MANUAL"), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Manuel kur aynı tarihte TCMB'den önceliklidir")
    void lookupRate_manualRateTakesPrecedenceOverTcmb() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        ExchangeRate manualRate = ExchangeRate.builder()
                .rate(new BigDecimal("34.627000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.MANUAL)
                .build();

        when(exchangeRateRepository.findExactRate("USD", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.of(manualRate));

        ExchangeRateDto result = conversionService.lookupRate("USD", "TRY", date, "SELLING");

        assertThat(result.rate()).isEqualByComparingTo("34.627000");
        assertThat(result.rateDate()).isEqualTo(date);
        assertThat(result.rateSource()).isEqualTo("MANUAL");
        assertThat(result.fallbackUsed()).isFalse();
        verify(exchangeRateRepository, never()).findExactRate(
                "USD", "TRY", date, RateType.SELLING, RateSource.TCMB);
    }

    @Test
    @DisplayName("EUR/TRY çevrimi Banker's Rounding ile 2 haneye yuvarlanmalı")
    void convert_eurToTry_appliesHalfEvenRounding() {
        LocalDate rateDate = LocalDate.of(2026, 7, 3);
        ExchangeRate rate = ExchangeRate.builder()
                .rate(new BigDecimal("35.000000"))
                .rateDate(rateDate)
                .rateType(RateType.SELLING)
                .build();

        when(exchangeRateRepository.findExactRate("EUR", "TRY", rateDate, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(rate));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal("100"),
                "EUR",
                "TRY",
                Instant.parse("2026-07-03T10:00:00Z"),
                "SELLING"
        );

        assertThat(result.convertedAmount()).isEqualByComparingTo("3500.00");
        assertThat(result.exchangeRate()).isEqualByComparingTo("35.000000");
        assertThat(result.fallbackRateUsed()).isFalse();
        verify(valueOperations).set(startsWith("rate:EUR:TRY:"), any(), any());
    }

    @Test
    @DisplayName("Hafta sonu için 5 günlük geriye fallback uygulanmalı")
    void convert_weekend_usesFallbackRate() {
        LocalDate saturday = LocalDate.of(2026, 7, 4);
        LocalDate friday = LocalDate.of(2026, 7, 3);
        ExchangeRate fridayRate = ExchangeRate.builder()
                .rate(new BigDecimal("35.000000"))
                .rateDate(friday)
                .rateType(RateType.SELLING)
                .build();

        when(exchangeRateRepository.findExactRate("EUR", "TRY", saturday, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "TRY", "SELLING", "TCMB", saturday, saturday.minusDays(5)))
                .thenReturn(Optional.of(fridayRate));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal("100"),
                "EUR",
                "TRY",
                Instant.parse("2026-07-04T12:00:00Z"),
                "SELLING"
        );

        assertThat(result.fallbackRateUsed()).isTrue();
        assertThat(result.rateDateUsed()).isEqualTo(friday);
        assertThat(result.convertedAmount()).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("JPY hedef para biriminde 0 ondalık — HALF_EVEN uygulanmalı")
    void convert_toJpy_roundsToZeroDecimals() {
        LocalDate rateDate = LocalDate.of(2026, 7, 3);
        ExchangeRate rate = ExchangeRate.builder()
                .rate(new BigDecimal("150.500000"))
                .rateDate(rateDate)
                .build();

        when(exchangeRateRepository.findExactRate("USD", "JPY", rateDate, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(rate));
        when(currencyRepository.findByCodeAndActiveTrue("JPY")).thenReturn(Optional.of(jpyCurrency));

        // 2 USD * 150.5 = 301.0 → 301
        ConversionResultDto result = conversionService.convert(
                new BigDecimal("2"),
                "USD",
                "JPY",
                Instant.parse("2026-07-03T10:00:00Z"),
                "SELLING"
        );

        assertThat(result.convertedAmount()).isEqualByComparingTo("301");
        assertThat(result.convertedAmount().scale()).isZero();
    }

    @Test
    @DisplayName("5 gün içinde kur bulunamazsa exception fırlatmalı")
    void convert_noRateWithinWindow_throws() {
        LocalDate date = LocalDate.of(2026, 7, 10);
        when(exchangeRateRepository.findExactRate(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert(
                BigDecimal.TEN, "EUR", "TRY",
                Instant.parse("2026-07-10T10:00:00Z"), "SELLING"))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    @Test
    @DisplayName("Aynı para birimi — kur 1.0, yuvarlama uygulanır")
    void convert_sameCurrency_returnsIdentityRate() {
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal("10.555"),
                "TRY",
                "TRY",
                Instant.parse("2026-07-03T10:00:00Z"),
                "SELLING"
        );

        assertThat(result.exchangeRate()).isEqualByComparingTo("1");
        assertThat(result.convertedAmount()).isEqualByComparingTo("10.56");
    }

    // =====================================================================
    // Banker's Rounding (HALF_EVEN) — yuvarlama SINIRI (tie) senaryoları
    // Kuruş kaybı politikası: .xx5 tam ortada kalan değer en yakın ÇİFT
    // haneye yuvarlanır (2.505 → 2.50, 2.515 → 2.52).
    // =====================================================================

    @ParameterizedTest(name = "Tutar {0} → HALF_EVEN(2) → {1}")
    @CsvSource({
            "2.505, 2.50",   // 0 çift → aşağı
            "2.515, 2.52",   // 1 tek  → yukarı
            "2.525, 2.52",   // 2 çift → aşağı
            "2.535, 2.54",   // 3 tek  → yukarı
            "2.545, 2.54"    // 4 çift → aşağı
    })
    @DisplayName("Yuvarlama sınırındaki tutarlar en yakın çift haneye yuvarlanır")
    void round_tieAmounts_roundToNearestEven(String amountStr, String expectedStr) {
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal(amountStr), "TRY", "TRY",
                Instant.parse("2026-07-05T10:00:00Z"), "SELLING");

        assertThat(result.convertedAmount()).isEqualByComparingTo(expectedStr);
        assertThat(result.convertedAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Çarpım sonucu tie üretirse de HALF_EVEN uygulanır (1 × 2.505000 → 2.50)")
    void convert_multiplicationProducesTie_appliesBankersRounding() {
        LocalDate date = LocalDate.of(2026, 7, 5);
        ExchangeRate rate = ExchangeRate.builder()
                .rate(new BigDecimal("2.505000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .build();
        when(exchangeRateRepository.findExactRate("EUR", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(rate));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                BigDecimal.ONE, "EUR", "TRY",
                Instant.parse("2026-07-05T10:00:00Z"), "SELLING");

        assertThat(result.convertedAmount()).isEqualByComparingTo("2.50");
    }

    // =====================================================================
    // Fallback penceresi — prompt senaryoları (işlem tarihi 05.07.2026)
    // Sınıf LENIENT modda olduğundan pencere sınırları verify() ile kanıtlanır.
    // =====================================================================

    @Test
    @DisplayName("Tam eşleşen kur varken fallback sorgusuna hiç gidilmez (05.07.2026)")
    void convert_exactDateMatch_neverQueriesFallback() {
        LocalDate date = LocalDate.of(2026, 7, 5);
        ExchangeRate rate = ExchangeRate.builder()
                .rate(new BigDecimal("35.000000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .build();
        when(exchangeRateRepository.findExactRate("EUR", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(rate));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal("100"), "EUR", "TRY",
                Instant.parse("2026-07-05T10:00:00Z"), "SELLING");

        assertThat(result.rateDateUsed()).isEqualTo(date);
        assertThat(result.fallbackRateUsed()).isFalse();
        assertThat(result.convertedAmount()).isEqualByComparingTo("3500.00");
        verify(exchangeRateRepository, never()).findNearestPastRate(
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("05.07.2026 kuru yok — 3 gün önceki 02.07.2026 kuru pencere içinde kullanılır")
    void convert_threeDayOldRate_withinFallbackWindow() {
        LocalDate requested = LocalDate.of(2026, 7, 5);
        LocalDate rateDate  = LocalDate.of(2026, 7, 2);   // 3 gün önce — limit içinde
        LocalDate windowMin = LocalDate.of(2026, 6, 30);  // requested - 5 gün

        ExchangeRate oldRate = ExchangeRate.builder()
                .rate(new BigDecimal("35.123456"))
                .rateDate(rateDate)
                .rateType(RateType.SELLING)
                .build();
        when(exchangeRateRepository.findExactRate("EUR", "TRY", requested, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "TRY", "SELLING", "TCMB", requested, windowMin))
                .thenReturn(Optional.of(oldRate));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));

        ConversionResultDto result = conversionService.convert(
                new BigDecimal("100"), "EUR", "TRY",
                Instant.parse("2026-07-05T10:00:00Z"), "SELLING");

        assertThat(result.fallbackRateUsed()).isTrue();
        assertThat(result.rateDateUsed()).isEqualTo(rateDate);
        // 100 × 35.123456 = 3512.3456 → HALF_EVEN(2) → 3512.35
        assertThat(result.convertedAmount()).isEqualByComparingTo("3512.35");
        // Pencere sınırının doğru hesaplandığının kanıtı (LENIENT'a rağmen)
        verify(exchangeRateRepository).findNearestPastRate(
                "EUR", "TRY", "SELLING", "TCMB", requested, windowMin);
    }

    @Test
    @DisplayName("En yakın kur 7 gün eski (28.06.2026) — 5 günlük limit aşıldığından exception")
    void convert_rateOlderThanFallbackLimit_throwsNotFound() {
        LocalDate requested = LocalDate.of(2026, 7, 5);
        LocalDate windowMin = LocalDate.of(2026, 6, 30);
        // DB'deki en yakın kayıt 28.06.2026 — windowMin'den ESKİ olduğundan
        // tarih-sınırlı sorgu boş döner; servis exception fırlatmalıdır.

        when(exchangeRateRepository.findExactRate("EUR", "TRY", requested, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "TRY", "SELLING", "TCMB", requested, windowMin))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert(
                new BigDecimal("100"), "EUR", "TRY",
                Instant.parse("2026-07-05T10:00:00Z"), "SELLING"))
                .isInstanceOf(ExchangeRateNotFoundException.class);

        // Sorgunun 30.06.2026 alt sınırıyla yapıldığı kanıtlanır —
        // 28.06 kaydının kullanılamamasının garantisi bu penceredir.
        verify(exchangeRateRepository).findNearestPastRate(
                "EUR", "TRY", "SELLING", "TCMB", requested, windowMin);
    }

    // =====================================================================
    // Efektif kur türü fallback — EFFECTIVE_* → BUYING / SELLING
    // =====================================================================

    @Test
    @DisplayName("EFFECTIVE_BUYING bulunamazsa BUYING kuruna düşülür")
    void lookupRate_effectiveBuying_fallsBackToBuying() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        ExchangeRate buyingRate = ExchangeRate.builder()
                .rate(new BigDecimal("32.400000"))
                .rateDate(date)
                .rateType(RateType.BUYING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("USD", "TRY", date, RateType.EFFECTIVE_BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "USD", "TRY", "EFFECTIVE_BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("USD", "TRY", date, RateType.BUYING, RateSource.TCMB))
                .thenReturn(Optional.of(buyingRate));

        ExchangeRateDto result = conversionService.lookupRate("USD", "TRY", date, "EFFECTIVE_BUYING");

        assertThat(result.rate()).isEqualByComparingTo("32.400000");
        assertThat(result.rateType()).isEqualTo("EFFECTIVE_BUYING");
        assertThat(result.fallbackUsed()).isTrue();
        verify(exchangeRateRepository).findExactRate("USD", "TRY", date, RateType.BUYING, RateSource.TCMB);
    }

    @Test
    @DisplayName("EFFECTIVE_SELLING bulunamazsa SELLING kuruna düşülür")
    void lookupRate_effectiveSelling_fallsBackToSelling() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        ExchangeRate sellingRate = ExchangeRate.builder()
                .rate(new BigDecimal("32.600000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("USD", "TRY", date, RateType.EFFECTIVE_SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "USD", "TRY", "EFFECTIVE_SELLING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("USD", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(sellingRate));

        ExchangeRateDto result = conversionService.lookupRate("USD", "TRY", date, "EFFECTIVE_SELLING");

        assertThat(result.rate()).isEqualByComparingTo("32.600000");
        assertThat(result.rateType()).isEqualTo("EFFECTIVE_SELLING");
        assertThat(result.fallbackUsed()).isTrue();
        verify(exchangeRateRepository).findExactRate("USD", "TRY", date, RateType.SELLING, RateSource.TCMB);
    }

    @Test
    @DisplayName("EFFECTIVE_BUYING ve BUYING yoksa exception fırlatılır")
    void lookupRate_effectiveBuying_noBaseType_throws() {
        LocalDate date = LocalDate.of(2026, 7, 7);

        when(exchangeRateRepository.findExactRate(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.lookupRate("USD", "TRY", date, "EFFECTIVE_BUYING"))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    @Test
    @DisplayName("Doğrudan kur yoksa TRY üzerinden çapraz kur hesaplanır (SAR -> JPY)")
    void lookupRate_triangulatesViaTry_whenDirectPairMissing() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        LocalDate legDate = LocalDate.of(2026, 7, 6);

        ExchangeRate sarTry = ExchangeRate.builder()
                .rate(new BigDecimal("8.640000"))
                .rateDate(legDate)
                .rateType(RateType.BUYING)
                .rateSource(RateSource.TCMB)
                .build();
        ExchangeRate jpyTry = ExchangeRate.builder()
                .rate(new BigDecimal("0.220000"))
                .rateDate(legDate)
                .rateType(RateType.BUYING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.EFFECTIVE_BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "EFFECTIVE_BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.EFFECTIVE_BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "EFFECTIVE_BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.EFFECTIVE_BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "EFFECTIVE_BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.EFFECTIVE_BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "EFFECTIVE_BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());

        when(exchangeRateRepository.findExactRate("SAR", "TRY", date, RateType.EFFECTIVE_BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "TRY", "EFFECTIVE_BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "TRY", date, RateType.EFFECTIVE_BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "TRY", "EFFECTIVE_BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "TRY", date, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "TRY", "BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "TRY", date, RateType.BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "TRY", "BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.of(sarTry));

        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.EFFECTIVE_BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "TRY", "EFFECTIVE_BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.EFFECTIVE_BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "TRY", "EFFECTIVE_BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "TRY", "BUYING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.BUYING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "TRY", "BUYING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.of(jpyTry));

        ExchangeRateDto result = conversionService.lookupRate("SAR", "JPY", date, "EFFECTIVE_BUYING");

        // 8.64 / 0.22 = 39.272727 → scale 6 → 39.272727
        assertThat(result.rate()).isEqualByComparingTo("39.272727");
        assertThat(result.rateType()).isEqualTo("EFFECTIVE_BUYING");
        assertThat(result.rateSource()).isEqualTo("TCMB");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    @DisplayName("Çapraz kurda manuel bacak varsa MANUAL kaynağı önceliklidir")
    void lookupRate_triangulation_manualLegTakesPrecedence() {
        LocalDate date = LocalDate.of(2026, 7, 7);

        ExchangeRate sarTry = ExchangeRate.builder()
                .rate(new BigDecimal("8.640000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.MANUAL)
                .build();
        ExchangeRate jpyTry = ExchangeRate.builder()
                .rate(new BigDecimal("0.220000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("SAR", "JPY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "SAR", "JPY", "SELLING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "SAR", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "SAR", "SELLING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());

        when(exchangeRateRepository.findExactRate("SAR", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.of(sarTry));
        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "JPY", "TRY", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("JPY", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(jpyTry));

        ExchangeRateDto result = conversionService.lookupRate("SAR", "JPY", date, "SELLING");

        assertThat(result.rateSource()).isEqualTo("MANUAL");
        assertThat(result.rate()).isEqualByComparingTo("39.272727");
    }

    @Test
    @DisplayName("AED/TRY doğrudan kur sorgusu başarılı olmalı")
    void lookupRate_aedToTry_returnsDirectRate() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        ExchangeRate aedTry = ExchangeRate.builder()
                .rate(new BigDecimal("8.895000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("AED", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "AED", "TRY", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("AED", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(aedTry));

        ExchangeRateDto result = conversionService.lookupRate("AED", "TRY", date, "SELLING");

        assertThat(result.rate()).isEqualByComparingTo("8.895000");
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    @DisplayName("AED/EUR çapraz kur TRY üzerinden hesaplanmalı")
    void lookupRate_aedToEur_triangulatesViaTry() {
        LocalDate date = LocalDate.of(2026, 7, 7);

        ExchangeRate aedTry = ExchangeRate.builder()
                .rate(new BigDecimal("8.895000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.TCMB)
                .build();
        ExchangeRate eurTry = ExchangeRate.builder()
                .rate(new BigDecimal("35.100000"))
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rateSource(RateSource.TCMB)
                .build();

        when(exchangeRateRepository.findExactRate("AED", "EUR", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "AED", "EUR", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("AED", "EUR", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "AED", "EUR", "SELLING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("EUR", "AED", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "AED", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("EUR", "AED", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "AED", "SELLING", "TCMB", date, date.minusDays(5)))
                .thenReturn(Optional.empty());

        when(exchangeRateRepository.findExactRate("AED", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "AED", "TRY", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("AED", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(aedTry));

        when(exchangeRateRepository.findExactRate("EUR", "TRY", date, RateType.SELLING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findNearestPastRate(
                "EUR", "TRY", "SELLING", "MANUAL", date, date.minusDays(5)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findExactRate("EUR", "TRY", date, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.of(eurTry));

        ExchangeRateDto result = conversionService.lookupRate("AED", "EUR", date, "SELLING");

        // 8.895 / 35.1 = 0.253418803… → scale 6 HALF_EVEN → 0.253419
        assertThat(result.rate()).isEqualByComparingTo("0.253419");
        assertThat(result.rateSource()).isEqualTo("TCMB");
    }
}
