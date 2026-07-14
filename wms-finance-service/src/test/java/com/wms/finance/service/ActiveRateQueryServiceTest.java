package com.wms.finance.service;

import com.wms.finance.dto.ActiveRateListDto;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.Currency;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActiveRateQueryService")
class ActiveRateQueryServiceTest {

    @Mock private CurrencyRepository currencyRepository;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private ActiveRateQueryService activeRateQueryService;

    @Test
    @DisplayName("Aktif kurlar lookup motoru üzerinden listelenir")
    void listActiveRates_delegatesToLookup() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        Currency usd = Currency.builder().code("USD").active(true).build();
        Currency tryCurrency = Currency.builder().code("TRY").active(true).build();

        ExchangeRateDto usdTry = new ExchangeRateDto(
                "USD", "TRY", new BigDecimal("32.600000"), date, "SELLING", "TCMB", false);

        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of(tryCurrency, usd));
        when(currencyConversionService.lookupRate("USD", "TRY", date, "SELLING")).thenReturn(usdTry);
        when(exchangeRateRepository.findLatestTcmbUpdateAt())
                .thenReturn(Optional.of(LocalDateTime.of(2026, 7, 7, 15, 35)));

        ActiveRateListDto result = activeRateQueryService.listActiveRates("TRY", date, "SELLING");

        assertThat(result.baseCurrency()).isEqualTo("TRY");
        assertThat(result.rateDate()).isEqualTo(date);
        assertThat(result.rates()).hasSize(1);
        assertThat(result.rates().getFirst().rate()).isEqualByComparingTo("32.600000");
        assertThat(result.lastTcmbSyncAt()).isEqualTo(LocalDateTime.of(2026, 7, 7, 15, 35));

        verify(currencyConversionService).lookupRate(eq("USD"), eq("TRY"), eq(date), eq("SELLING"));
    }

    @Test
    @DisplayName("Kur bulunamayan döviz atlanır, liste kısmi döner (rollback-only olmaz)")
    void listActiveRates_skipsMissingRates() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        Currency usd = Currency.builder().code("USD").active(true).build();
        Currency aed = Currency.builder().code("AED").active(true).build();

        when(currencyRepository.findAllByActiveTrue()).thenReturn(List.of(usd, aed));
        when(currencyConversionService.lookupRate("USD", "TRY", date, "SELLING"))
                .thenReturn(new ExchangeRateDto("USD", "TRY", BigDecimal.TEN, date, "SELLING", "TCMB", false));
        when(currencyConversionService.lookupRate("AED", "TRY", date, "SELLING"))
                .thenThrow(new ExchangeRateNotFoundException("AED", "TRY", "SELLING"));
        when(exchangeRateRepository.findLatestTcmbUpdateAt()).thenReturn(Optional.empty());

        ActiveRateListDto result = activeRateQueryService.listActiveRates("TRY", date, "SELLING");

        assertThat(result.rates()).hasSize(1);
        assertThat(result.rates().getFirst().sourceCurrency()).isEqualTo("USD");
    }
}
