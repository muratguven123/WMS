package com.wms.finance.service;

import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxType;
import com.wms.finance.exception.TaxResolutionException;
import com.wms.finance.repository.TaxRateRepository;
import com.wms.finance.repository.TaxTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxResolutionService")
class TaxResolutionServiceTest {

    @Mock TaxTypeRepository taxTypeRepository;
    @Mock TaxRateRepository taxRateRepository;

    @InjectMocks TaxResolutionService service;

    private static final Long TAX_TYPE_ID = 1L;
    private static final Long COUNTRY_ID = 1L;
    private static final Long LOCATION_ID = 1L;
    private static final Long CUSTOMER_ID = 1L;
    private static final String PRODUCT_TYPE = "ELECTRONIC";
    private static final LocalDate TX_DATE = LocalDate.of(2025, 6, 1);

    private TaxType activeTaxType() {
        TaxType taxType = TaxType.builder()
                .id(TAX_TYPE_ID)
                .code("KDV")
                .name("Katma Değer Vergisi")
                .active(true)
                .build();
        when(taxTypeRepository.findByCode("KDV")).thenReturn(Optional.of(taxType));
        return taxType;
    }

    private TaxRate rate(TaxType taxType, BigDecimal value, Long locationId, Long customerId, String productType) {
        return TaxRate.builder()
                .id(1L)
                .taxType(taxType)
                .countryId(COUNTRY_ID)
                .locationId(locationId)
                .customerId(customerId)
                .productType(productType)
                .rate(value)
                .startDate(LocalDate.of(2025, 1, 1))
                .active(true)
                .build();
    }

    private TaxResolutionContext ctx() {
        return new TaxResolutionContext(COUNTRY_ID, LOCATION_ID, CUSTOMER_ID, PRODUCT_TYPE, null);
    }

    private void givenCandidates(TaxRate... rates) {
        when(taxRateRepository.findApplicableRates(
                eq(TAX_TYPE_ID), eq(COUNTRY_ID), any(), any(), any(), any(), eq(TX_DATE)))
                .thenReturn(List.of(rates));
    }

    @Nested
    @DisplayName("Best-match öncelik")
    class Priority {

        @Test
        @DisplayName("Öncelik-1: Location + Customer + ProductType")
        void priority1_fullMatch() {
            TaxType taxType = activeTaxType();
            givenCandidates(
                    rate(taxType, new BigDecimal("20.00"), null, null, null),
                    rate(taxType, new BigDecimal("18.00"), LOCATION_ID, CUSTOMER_ID, PRODUCT_TYPE));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, ctx()))
                    .isEqualByComparingTo("18.00");
        }

        @Test
        @DisplayName("Öncelik-2: Location + ProductType")
        void priority2_locationAndProduct() {
            TaxType taxType = activeTaxType();
            TaxResolutionContext context =
                    new TaxResolutionContext(COUNTRY_ID, LOCATION_ID, null, PRODUCT_TYPE, null);
            givenCandidates(
                    rate(taxType, new BigDecimal("20.00"), null, null, null),
                    rate(taxType, new BigDecimal("10.00"), LOCATION_ID, null, PRODUCT_TYPE));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, context))
                    .isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Öncelik-3: Customer + ProductType")
        void priority3_customerAndProduct() {
            TaxType taxType = activeTaxType();
            TaxResolutionContext context =
                    new TaxResolutionContext(COUNTRY_ID, null, CUSTOMER_ID, PRODUCT_TYPE, null);
            givenCandidates(
                    rate(taxType, new BigDecimal("20.00"), null, null, null),
                    rate(taxType, new BigDecimal("8.00"), null, CUSTOMER_ID, PRODUCT_TYPE));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, context))
                    .isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("Öncelik-4: ProductType yalnız")
        void priority4_productOnly() {
            TaxType taxType = activeTaxType();
            TaxResolutionContext context =
                    new TaxResolutionContext(COUNTRY_ID, null, null, PRODUCT_TYPE, null);
            givenCandidates(
                    rate(taxType, new BigDecimal("20.00"), null, null, null),
                    rate(taxType, new BigDecimal("1.00"), null, null, PRODUCT_TYPE));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, context))
                    .isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("Öncelik-5: Ülke varsayılanı")
        void priority5_countryDefault() {
            TaxType taxType = activeTaxType();
            givenCandidates(rate(taxType, new BigDecimal("20.00"), null, null, null));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, ctx()))
                    .isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("Lokasyon kuralı, bağlamda lokasyon yoksa elenir")
        void locationRule_excludedWhenContextHasNoLocation() {
            TaxType taxType = activeTaxType();
            TaxResolutionContext context =
                    new TaxResolutionContext(COUNTRY_ID, null, null, PRODUCT_TYPE, null);
            givenCandidates(
                    rate(taxType, new BigDecimal("5.00"), LOCATION_ID, null, PRODUCT_TYPE),
                    rate(taxType, new BigDecimal("20.00"), null, null, null));

            assertThat(service.resolveTaxRate("KDV", TX_DATE, context))
                    .isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("Hata senaryoları")
    class Errors {

        @Test
        @DisplayName("Oran bulunamazsa TaxResolutionException")
        void noRate_throws() {
            activeTaxType();
            givenCandidates();

            assertThatThrownBy(() -> service.resolveTaxRate("KDV", TX_DATE, ctx()))
                    .isInstanceOf(TaxResolutionException.class);
        }

        @Test
        @DisplayName("Pasif vergi tipi için exception")
        void inactiveTaxType_throws() {
            when(taxTypeRepository.findByCode("KDV")).thenReturn(Optional.of(
                    TaxType.builder().id(TAX_TYPE_ID).code("KDV").name("KDV").active(false).build()));

            assertThatThrownBy(() -> service.resolveTaxRate("KDV", TX_DATE, ctx()))
                    .isInstanceOf(TaxResolutionException.class);
        }

        @Test
        @DisplayName("countryId null ise IllegalArgumentException")
        void nullCountry_throws() {
            assertThatThrownBy(() -> new TaxResolutionContext(null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("OperationType filtresi")
    class OperationType {

        @Test
        @DisplayName("Eşleşmeyen operationType elenir")
        void mismatchedOperation_filtered() {
            TaxType taxType = activeTaxType();
            TaxRate inbound = rate(taxType, new BigDecimal("5.00"), null, null, null);
            inbound.setOperationType("INBOUND");
            TaxRate general = rate(taxType, new BigDecimal("18.00"), null, null, null);

            givenCandidates(inbound, general);

            TaxResolutionContext context =
                    new TaxResolutionContext(COUNTRY_ID, null, null, null, "OUTBOUND");

            assertThat(service.resolveTaxRate("KDV", TX_DATE, context))
                    .isEqualByComparingTo("18.00");
        }
    }

    @Nested
    @DisplayName("Eşit skor tie-breaker")
    class TieBreaker {

        @Test
        @DisplayName("Aynı skorda daha yeni startDate kazanır")
        void newerStartDateWins() {
            TaxType taxType = activeTaxType();
            TaxRate older = rate(taxType, new BigDecimal("18.00"), null, null, null);
            older.setStartDate(LocalDate.of(2024, 1, 1));
            TaxRate newer = rate(taxType, new BigDecimal("20.00"), null, null, null);
            newer.setStartDate(LocalDate.of(2025, 3, 1));

            givenCandidates(older, newer);

            assertThat(service.resolveTaxRate("KDV", TX_DATE,
                    TaxResolutionContext.ofCountry(COUNTRY_ID)))
                    .isEqualByComparingTo("20.00");
        }
    }
}
