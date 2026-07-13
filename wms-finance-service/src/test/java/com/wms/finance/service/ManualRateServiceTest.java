package com.wms.finance.service;

import com.wms.finance.dto.ManualRateRequest;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualRateService")
class ManualRateServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private ExchangeRateAuditLogRepository auditLogRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ExchangeRateCacheService cacheService;

    @InjectMocks
    private ManualRateService manualRateService;

    private static final Long USER_ID = 1L;
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 7, 3);

    @Test
    @DisplayName("Yeni manuel kur INSERT eder ve audit log yazar")
    void upsert_insertsNewRate() {
        Currency usd = currency("USD");
        Currency try_ = currency("TRY");
        ManualRateRequest request = new ManualRateRequest(
                "USD", "TRY", RATE_DATE, RateType.BUYING, new BigDecimal("32.100000"));

        when(currencyRepository.findByCodeAndActiveTrue("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(try_));
        when(exchangeRateRepository.findExactRate(
                "USD", "TRY", RATE_DATE, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> {
            ExchangeRate rate = inv.getArgument(0);
            rate.setId(1L);
            return rate;
        });

        var response = manualRateService.upsert(request, USER_ID);

        assertThat(response.action()).isEqualTo(AuditActionType.INSERT);
        assertThat(response.rate()).isEqualByComparingTo("32.100000");

        ArgumentCaptor<ExchangeRateAuditLog> auditCaptor = ArgumentCaptor.forClass(ExchangeRateAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo(AuditActionType.INSERT);
        assertThat(auditCaptor.getValue().getUserId()).isEqualTo(USER_ID);

        verify(cacheService).evict("USD", "TRY", RATE_DATE);
    }

    @Test
    @DisplayName("Mevcut manuel kur UPDATE eder ve eski değeri audit'e yazar")
    void upsert_updatesExistingRate() {
        Currency usd = currency("USD");
        Currency try_ = currency("TRY");
        ExchangeRate existing = ExchangeRate.builder()
                .id(1L)
                .sourceCurrency(usd)
                .targetCurrency(try_)
                .rateDate(RATE_DATE)
                .rateType(RateType.BUYING)
                .rateSource(RateSource.MANUAL)
                .rate(new BigDecimal("32.000000"))
                .build();

        ManualRateRequest request = new ManualRateRequest(
                "USD", "TRY", RATE_DATE, RateType.BUYING, new BigDecimal("32.500000"));

        when(currencyRepository.findByCodeAndActiveTrue("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(try_));
        when(exchangeRateRepository.findExactRate(
                "USD", "TRY", RATE_DATE, RateType.BUYING, RateSource.MANUAL))
                .thenReturn(Optional.of(existing));

        var response = manualRateService.upsert(request, USER_ID);

        assertThat(response.action()).isEqualTo(AuditActionType.UPDATE);
        assertThat(response.previousRate()).isEqualByComparingTo("32.000000");

        ArgumentCaptor<ExchangeRateAuditLog> auditCaptor = ArgumentCaptor.forClass(ExchangeRateAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOldRate()).isEqualByComparingTo("32.000000");
        assertThat(auditCaptor.getValue().getNewRate()).isEqualByComparingTo("32.500000");

        verify(cacheService).evict("USD", "TRY", RATE_DATE);
    }

    private Currency currency(String code) {
        Currency c = Currency.builder().code(code).active(true).decimalPlaces(2).build();
        c.setId(1L);
        return c;
    }
}
