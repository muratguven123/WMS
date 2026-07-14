package com.wms.finance.service;

import com.wms.finance.dto.CustomerExchangeRateRequest;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.*;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.ExchangeDiffPreference;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.*;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerRateService")
class CustomerRateServiceTest {

    private static final Long CUSTOMER_ID = 1L;

    @Mock private FinanceCustomerRepository customerRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private CustomerExchangeRateRepository customerExchangeRateRepository;
    @Mock private ContractFixedRateRepository contractFixedRateRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ExchangeRateAuditLogRepository auditLogRepository;
    @Mock private ExchangeRateCacheService cacheService;
    @Mock private CustomerCurrencyValidator customerCurrencyValidator;

    @InjectMocks
    private CustomerRateService customerRateService;

    private FinanceCustomer customerWith(RateType type, RateSource source) {
        return FinanceCustomer.builder()
                .id(CUSTOMER_ID)
                .rateType(type)
                .rateSource(source)
                .exchangeDiffPreference(ExchangeDiffPreference.PER_INVOICE)
                .build();
    }

    @Nested
    @DisplayName("getCustomerRate with CONTRACT source")
    class ContractFixedRatesTests {

        @Test
        @DisplayName("Gecerli tarih araliginda sozlesme sabit kuru kullanilir")
        void usesContractFixedRateWhenValid() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            Instant instant = Instant.parse("2026-07-03T10:00:00Z");

            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CONTRACT);
            Contract contract = Contract.builder().id(100L).customer(customer).build();

            ContractFixedRate fixedRate = ContractFixedRate.builder()
                    .contract(contract)
                    .rate(new BigDecimal("35.500000"))
                    .validFrom(LocalDate.of(2026, 7, 1))
                    .validTo(LocalDate.of(2026, 7, 10))
                    .build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customer));
            when(contractRepository.findActiveContracts(eq(CUSTOMER_ID), any(LocalDateTime.class)))
                    .thenReturn(List.of(contract));
            when(contractFixedRateRepository.findActiveRates(100L, "EUR", "TRY", RateType.SELLING))
                    .thenReturn(List.of(fixedRate));

            var result = customerRateService.getCustomerRate(CUSTOMER_ID, "EUR", "TRY", instant);

            assertThat(result.rate()).isEqualByComparingTo("35.500000");
            assertThat(result.rateSource()).isEqualTo("CONTRACT");
            assertThat(result.fallbackUsed()).isFalse();
        }

        @Test
        @DisplayName("Tarih araligi disindaysa sistem kuruna fallback yapilir")
        void fallsBackToSystemRateWhenOutsideValidity() {
            LocalDate date = LocalDate.of(2026, 7, 15);
            Instant instant = Instant.parse("2026-07-15T10:00:00Z");

            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CONTRACT);
            Contract contract = Contract.builder().id(100L).customer(customer).build();

            ContractFixedRate fixedRate = ContractFixedRate.builder()
                    .contract(contract)
                    .rate(new BigDecimal("35.500000"))
                    .validFrom(LocalDate.of(2026, 7, 1))
                    .validTo(LocalDate.of(2026, 7, 10))
                    .build();

            ExchangeRate systemRate = ExchangeRate.builder()
                    .rate(new BigDecimal("36.000000"))
                    .rateDate(date)
                    .rateType(RateType.SELLING)
                    .rateSource(RateSource.TCMB)
                    .build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customer));
            when(contractRepository.findActiveContracts(eq(CUSTOMER_ID), any(LocalDateTime.class)))
                    .thenReturn(List.of(contract));
            when(contractFixedRateRepository.findActiveRates(100L, "EUR", "TRY", RateType.SELLING))
                    .thenReturn(List.of(fixedRate));
            when(exchangeRateRepository.findNearestPastRate(eq("EUR"), eq("TRY"), eq("SELLING"), eq("MANUAL"), eq(date), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(exchangeRateRepository.findNearestPastRate(eq("EUR"), eq("TRY"), eq("SELLING"), eq("TCMB"), eq(date), any(LocalDate.class)))
                    .thenReturn(Optional.of(systemRate));

            var result = customerRateService.getCustomerRate(CUSTOMER_ID, "EUR", "TRY", instant);

            assertThat(result.rate()).isEqualByComparingTo("36.000000");
            assertThat(result.rateSource()).isEqualTo("TCMB");
            assertThat(result.fallbackUsed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getCustomerRate with CUSTOMER source")
    class CustomerExchangeRatesTests {

        @Test
        @DisplayName("Musteri ozel kuru bulundugunda kullanilir")
        void usesCustomerExchangeRateWhenFound() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            Instant instant = Instant.parse("2026-07-03T10:00:00Z");

            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CUSTOMER);
            CustomerExchangeRate custRate = CustomerExchangeRate.builder()
                    .customer(customer)
                    .rate(new BigDecimal("34.200000"))
                    .rateDate(date)
                    .rateType(RateType.SELLING)
                    .build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customer));
            when(customerExchangeRateRepository.findExactRate(CUSTOMER_ID, "EUR", "TRY", date, RateType.SELLING))
                    .thenReturn(Optional.of(custRate));

            var result = customerRateService.getCustomerRate(CUSTOMER_ID, "EUR", "TRY", instant);

            assertThat(result.rate()).isEqualByComparingTo("34.200000");
            assertThat(result.rateSource()).isEqualTo("CUSTOMER");
            assertThat(result.fallbackUsed()).isFalse();
        }

        @Test
        @DisplayName("Strict modda ozel kur bulunamazsa exception firlatilir")
        void throwsWhenCustomerRateMissingInStrict() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            Instant instant = Instant.parse("2026-07-03T10:00:00Z");

            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CUSTOMER);
            SystemConfig config = SystemConfig.builder().strictCustomerRate(true).build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customer));
            when(customerExchangeRateRepository.findExactRate(CUSTOMER_ID, "EUR", "TRY", date, RateType.SELLING))
                    .thenReturn(Optional.empty());
            when(systemConfigRepository.findFirstWithDefaultCurrency())
                    .thenReturn(Optional.of(config));

            assertThatThrownBy(() -> customerRateService.getCustomerRate(CUSTOMER_ID, "EUR", "TRY", instant))
                    .isInstanceOf(ExchangeRateNotFoundException.class);
        }

        @Test
        @DisplayName("Non-strict modda ozel kur bulunamazsa sistem kuruna fallback yapilir")
        void fallsBackToSystemRateInNonStrict() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            Instant instant = Instant.parse("2026-07-03T10:00:00Z");

            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CUSTOMER);
            SystemConfig config = SystemConfig.builder().strictCustomerRate(false).build();
            ExchangeRate systemRate = ExchangeRate.builder()
                    .rate(new BigDecimal("33.500000"))
                    .rateDate(date)
                    .rateType(RateType.SELLING)
                    .rateSource(RateSource.MANUAL)
                    .build();

            when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                    .thenReturn(Optional.of(customer));
            when(customerExchangeRateRepository.findExactRate(CUSTOMER_ID, "EUR", "TRY", date, RateType.SELLING))
                    .thenReturn(Optional.empty());
            when(systemConfigRepository.findFirstWithDefaultCurrency())
                    .thenReturn(Optional.of(config));
            when(exchangeRateRepository.findNearestPastRate(eq("EUR"), eq("TRY"), eq("SELLING"), eq("MANUAL"), eq(date), any(LocalDate.class)))
                    .thenReturn(Optional.of(systemRate));

            var result = customerRateService.getCustomerRate(CUSTOMER_ID, "EUR", "TRY", instant);

            assertThat(result.rate()).isEqualByComparingTo("33.500000");
            assertThat(result.rateSource()).isEqualTo("MANUAL");
            assertThat(result.fallbackUsed()).isTrue();
        }
    }

    @Nested
    @DisplayName("upsertCustomerRate")
    class UpsertCustomerRateTests {

        @Test
        @DisplayName("Musteri ozel kuru basariyla eklenir ve audit log yazilir")
        void upsertsCustomerRateSuccessfully() {
            LocalDate date = LocalDate.of(2026, 7, 3);
            FinanceCustomer customer = customerWith(RateType.SELLING, RateSource.CUSTOMER);
            Currency eur = Currency.builder().id(10L).code("EUR").active(true).build();
            Currency tryCur = Currency.builder().id(11L).code("TRY").active(true).build();

            CustomerExchangeRateRequest req = new CustomerExchangeRateRequest("EUR", "TRY", date, RateType.SELLING, new BigDecimal("35.000000"));

            when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
            when(currencyRepository.findByCodeAndActiveTrue("EUR")).thenReturn(Optional.of(eur));
            when(currencyRepository.findByCodeAndActiveTrue("TRY")).thenReturn(Optional.of(tryCur));
            when(customerExchangeRateRepository.findExactRate(CUSTOMER_ID, "EUR", "TRY", date, RateType.SELLING))
                    .thenReturn(Optional.empty());

            CustomerExchangeRate savedRate = CustomerExchangeRate.builder()
                    .id(500L)
                    .customer(customer)
                    .sourceCurrency(eur)
                    .targetCurrency(tryCur)
                    .rateDate(date)
                    .rateType(RateType.SELLING)
                    .rate(new BigDecimal("35.000000"))
                    .build();

            when(customerExchangeRateRepository.save(any(CustomerExchangeRate.class))).thenReturn(savedRate);

            var result = customerRateService.upsertCustomerRate(CUSTOMER_ID, req, 999L);

            assertThat(result.rate()).isEqualByComparingTo("35.000000");
            assertThat(result.action()).isEqualTo(AuditActionType.INSERT);

            verify(auditLogRepository, times(1)).save(any(ExchangeRateAuditLog.class));
            verify(cacheService, times(1)).evict("EUR", "TRY", date);
        }
    }
}
