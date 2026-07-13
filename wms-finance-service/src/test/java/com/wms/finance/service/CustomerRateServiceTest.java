package com.wms.finance.service;

import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.FinanceCustomer;
import com.wms.finance.entity.enums.ExchangeDiffPreference;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.ExchangeRateRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerRateService")
class CustomerRateServiceTest {

    private static final Long CUSTOMER_ID = 1L;

    @Mock private FinanceCustomerRepository customerRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private CustomerRateService customerRateService;

    private FinanceCustomer customerWith(RateType type, RateSource source) {
        return FinanceCustomer.builder()
                .rateType(type)
                .rateSource(source)
                .exchangeDiffPreference(ExchangeDiffPreference.PER_INVOICE)
                .build();
    }

    @Nested
    @DisplayName("getCustomerRate")
    class GetCustomerRate {

        @Test
        @DisplayName("Müşteri rateType ve rateSource ile kur sorgular")
        void usesCustomerRateParameters() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            ExchangeRate rate = ExchangeRate.builder()
                    .rate(new BigDecimal("35.000000"))
                    .rateDate(date)
                    .rateType(RateType.SELLING)
                    .rateSource(RateSource.TCMB)
                    .build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customerWith(RateType.SELLING, RateSource.TCMB)));
            when(exchangeRateRepository.findExactRate(
                    "EUR", "TRY", date, RateType.SELLING, RateSource.TCMB))
                    .thenReturn(Optional.of(rate));

            var result = customerRateService.getCustomerRate(
                    CUSTOMER_ID, "EUR", "TRY", Instant.parse("2026-07-03T10:00:00Z"));

            assertThat(result.rate()).isEqualByComparingTo("35.000000");
            assertThat(result.rateType()).isEqualTo("SELLING");
            assertThat(result.rateSource()).isEqualTo("TCMB");
            assertThat(result.fallbackUsed()).isFalse();
        }

        @Test
        @DisplayName("Kur bulunamazsa exception fırlatır")
        void throwsWhenRateMissing() {
            LocalDate date = LocalDate.of(2026, 7, 10);
            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customerWith(RateType.BUYING, RateSource.MANUAL)));
            when(exchangeRateRepository.findExactRate(
                    eq("USD"), eq("TRY"), eq(date), eq(RateType.BUYING), eq(RateSource.MANUAL)))
                    .thenReturn(Optional.empty());
            when(exchangeRateRepository.findNearestPastRate(
                    eq("USD"), eq("TRY"), eq("BUYING"), eq("MANUAL"), eq(date), eq(date.minusDays(5))))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerRateService.getCustomerRate(
                    CUSTOMER_ID, "USD", "TRY", Instant.parse("2026-07-10T10:00:00Z")))
                    .isInstanceOf(ExchangeRateNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("shouldCalculateExchangeDiff")
    class ExchangeDiffRules {

        @Test
        @DisplayName("NONE her zaman false döner")
        void noneReturnsFalse() {
            assertThat(customerRateService.shouldCalculateExchangeDiff(
                    ExchangeDiffPreference.NONE, CustomerRateService.ExchangeDiffContext.PER_INVOICE))
                    .isFalse();
        }

        @Test
        @DisplayName("PER_INVOICE yalnızca fatura bağlamında true döner")
        void perInvoiceContext() {
            assertThat(customerRateService.shouldCalculateExchangeDiff(
                    ExchangeDiffPreference.PER_INVOICE,
                    CustomerRateService.ExchangeDiffContext.PER_INVOICE))
                    .isTrue();
            assertThat(customerRateService.shouldCalculateExchangeDiff(
                    ExchangeDiffPreference.PER_INVOICE,
                    CustomerRateService.ExchangeDiffContext.MONTHLY))
                    .isFalse();
        }

        @Test
        @DisplayName("MONTHLY yalnızca aylık bağlamda true döner")
        void monthlyContext() {
            assertThat(customerRateService.shouldCalculateExchangeDiff(
                    ExchangeDiffPreference.MONTHLY,
                    CustomerRateService.ExchangeDiffContext.MONTHLY))
                    .isTrue();
        }
    }
}
