package com.wms.finance.service;

import com.wms.finance.dto.TcmbUpsertResult;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.integration.tcmb.TcmbParsedRate;
import com.wms.finance.integration.tcmb.TcmbSyncNotificationService;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TcmbRateUpsertService")
class TcmbRateUpsertServiceTest {

    @Mock private TcmbSyncNotificationService notificationService;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private ExchangeRateAuditLogRepository auditLogRepository;
    @Mock private ExchangeRateCacheService cacheService;

    @InjectMocks
    private TcmbRateUpsertService tcmbRateUpsertService;

    private Currency tryCurrency;
    private LocalDate rateDate;

    @BeforeEach
    void setUp() {
        rateDate = LocalDate.of(2026, 7, 7);
        tryCurrency = Currency.builder().id(1L).code("TRY").active(true).build();
    }

    @Test
    @DisplayName("Bilinmeyen para birimi destekleniyorsa otomatik oluşturulur")
    void upsertRates_createsMissingCurrency() {
        TcmbParsedRate parsed = new TcmbParsedRate(
                "CHF", rateDate, RateType.SELLING, new BigDecimal("39.500000"));

        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));
        when(currencyRepository.findByCodeAndActiveTrue("CHF")).thenReturn(Optional.empty());
        when(currencyRepository.save(any(Currency.class))).thenAnswer(inv -> {
            Currency c = inv.getArgument(0);
            c.setId(9L);
            return c;
        });
        when(exchangeRateRepository.findExactRate(
                "CHF", "TRY", rateDate, RateType.SELLING, RateSource.TCMB))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        TcmbUpsertResult result = tcmbRateUpsertService.upsertRates(List.of(parsed));

        ArgumentCaptor<Currency> currencyCaptor = ArgumentCaptor.forClass(Currency.class);
        verify(currencyRepository).save(currencyCaptor.capture());
        assertThat(currencyCaptor.getValue().getCode()).isEqualTo("CHF");
        assertThat(result.inserted()).isEqualTo(1);
        verify(cacheService).evictAllForDate(rateDate);
    }

    @Test
    @DisplayName("Desteklenmeyen para birimi atlanır")
    void upsertRates_skipsUnsupportedCurrency() {
        TcmbParsedRate parsed = new TcmbParsedRate(
                "PLN", rateDate, RateType.SELLING, new BigDecimal("9.000000"));

        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCurrency));
        when(currencyRepository.findByCodeAndActiveTrue("PLN")).thenReturn(Optional.empty());

        TcmbUpsertResult result = tcmbRateUpsertService.upsertRates(List.of(parsed));

        assertThat(result.skipped()).isEqualTo(1);
        verify(currencyRepository, never()).save(any(Currency.class));
        verify(exchangeRateRepository, never()).save(any(ExchangeRate.class));
    }
}
