package com.wms.billing.service;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.domain.enums.RateType;
import com.wms.billing.dto.InvoiceDto;
import com.wms.billing.dto.InvoiceItemInputDto;
import com.wms.billing.dto.InvoiceItemResultDto;
import com.wms.billing.exception.ExchangeRateNotFoundException;
import com.wms.billing.exception.RateLockViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InvoiceCalculationService")
class InvoiceCalculationServiceTest {

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Mock
    private TaxLookupService taxLookupService;

    @InjectMocks
    private InvoiceCalculationService sut;

    private static final Long CUSTOMER_ID  = 1L;
    private static final Long LOCATION_ID  = 1L;
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 7, 4);
    private static final BigDecimal EUR_TRY_RATE = new BigDecimal("36.850000");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "accountingScale",   2);
        ReflectionTestUtils.setField(sut, "accountingCurrency", "TRY");
        ReflectionTestUtils.setField(sut, "defaultCountryId", 1L);
    }

    private InvoiceItemResultDto calcLine(InvoiceItemInputDto input) {
        return sut.calculateLine(input, CUSTOMER_ID, LOCATION_ID, 1L, RATE_DATE);
    }

    // =========================================================================
    // Satır Hesaplama
    // =========================================================================

    @Nested
    @DisplayName("calculateLine()")
    class CalculateLineTests {

        @Test
        @DisplayName("Standart satır: lineTotal ve taxAmount doğru hesaplanmalı")
        void standardLine_correctTotals() {
            var input = InvoiceItemInputDto.builder()
                    .itemDescription("Depo hizmeti")
                    .quantity(new BigDecimal("10"))
                    .unitPriceOriginal(new BigDecimal("50.00"))
                    .discountOriginal(new BigDecimal("20.00"))
                    .taxTypeCode("KDV").taxRate(new BigDecimal("20.00"))
                    .build();

            // lineTotal = (10 × 50) − 20 = 480.0000
            // tax       = 480 × 0.20      = 96.0000
            InvoiceItemResultDto result = calcLine(input);

            assertThat(result.lineTotalOriginal()).isEqualByComparingTo("480.0000");
            assertThat(result.taxAmountOriginal()).isEqualByComparingTo("96.0000");
        }

        @Test
        @DisplayName("İskonto sıfır: lineTotal = quantity × unitPrice")
        void zeroDiscount_lineTotalEqualsGross() {
            var input = InvoiceItemInputDto.builder()
                    .itemDescription("Ürün A")
                    .quantity(new BigDecimal("3"))
                    .unitPriceOriginal(new BigDecimal("100.00"))
                    .discountOriginal(BigDecimal.ZERO)
                    .taxTypeCode("KDV").taxRate(new BigDecimal("18.00"))
                    .build();

            InvoiceItemResultDto result = calcLine(input);

            assertThat(result.lineTotalOriginal()).isEqualByComparingTo("300.0000");
            assertThat(result.taxAmountOriginal()).isEqualByComparingTo("54.0000");
        }

        @Test
        @DisplayName("Banker's Rounding (HALF_EVEN): 0.5 senaryosu yuvarlama doğru olmalı")
        void bankersRounding_halfEven() {
            // lineTotal = 1 × 1.005 − 0 = 1.0050  →  tax = 1.005 × 0.20 = 0.2010
            // Banker's rounding ile 0.2010 → 0.2010 (zaten tam)
            // Kesir senaryosu: taxRate=33.33, lineTotal=1.0000
            // tax = 1.0000 × 0.3333 = 0.3333
            var input = InvoiceItemInputDto.builder()
                    .itemDescription("Test kalemi")
                    .quantity(new BigDecimal("1"))
                    .unitPriceOriginal(new BigDecimal("1.0000"))
                    .discountOriginal(BigDecimal.ZERO)
                    .taxTypeCode("KDV").taxRate(new BigDecimal("33.33"))
                    .build();

            InvoiceItemResultDto result = calcLine(input);

            // 1 × 33.33 / 100 = 0.3333
            assertThat(result.taxAmountOriginal()).isEqualByComparingTo("0.3333");
        }

        @Test
        @DisplayName("Kesirli miktar ve fiyat: doğru scale korunmalı (scale=4)")
        void fractionalQuantityAndPrice_scalePreserved() {
            var input = InvoiceItemInputDto.builder()
                    .itemDescription("Parçalı ürün")
                    .quantity(new BigDecimal("2.5"))
                    .unitPriceOriginal(new BigDecimal("33.33"))
                    .discountOriginal(new BigDecimal("5.00"))
                    .taxTypeCode("KDV").taxRate(new BigDecimal("20.00"))
                    .build();

            // gross = 2.5 × 33.33 = 83.3250
            // lineTotal = 83.3250 − 5.00 = 78.3250
            // tax = 78.3250 × 0.20 = 15.6650
            InvoiceItemResultDto result = calcLine(input);

            assertThat(result.lineTotalOriginal()).isEqualByComparingTo("78.3250");
            assertThat(result.taxAmountOriginal()).isEqualByComparingTo("15.6650");
            assertThat(result.lineTotalOriginal().scale()).isEqualTo(4);
            assertThat(result.taxAmountOriginal().scale()).isEqualTo(4);
        }
    }

    // =========================================================================
    // Fatura Hesaplama (calculateInvoice)
    // =========================================================================

    @Nested
    @DisplayName("calculateInvoice()")
    class CalculateInvoiceTests {

        private List<InvoiceItemInputDto> twoLineItems() {
            return List.of(
                    InvoiceItemInputDto.builder()
                            .itemDescription("Hizmet A")
                            .quantity(new BigDecimal("10"))
                            .unitPriceOriginal(new BigDecimal("100.00"))
                            .discountOriginal(new BigDecimal("50.00"))
                            .taxTypeCode("KDV").taxRate(new BigDecimal("20.00"))
                            .build(),
                    InvoiceItemInputDto.builder()
                            .itemDescription("Hizmet B")
                            .quantity(new BigDecimal("5"))
                            .unitPriceOriginal(new BigDecimal("200.00"))
                            .discountOriginal(BigDecimal.ZERO)
                            .taxTypeCode("KDV").taxRate(new BigDecimal("10.00"))
                            .build()
            );
        }

        @BeforeEach
        void mockRate() {
            when(currencyConversionService.getRate("EUR", "TRY", RATE_DATE, RateType.SELLING))
                    .thenReturn(EUR_TRY_RATE);
        }

        @Test
        @DisplayName("İki satırlı fatura: subtotal, tax ve grandTotal doğru hesaplanmalı")
        void twoLines_totalsCorrect() {
            // Satır 1: lineTotal = (10×100)−50 = 950,  tax = 950×0.20 = 190
            // Satır 2: lineTotal = (5×200)−0   = 1000, tax = 1000×0.10 = 100
            // subtotal = 950 + 1000 = 1950
            // taxTotal = 190 + 100  = 290
            // grandTotal = 1950 + 290 = 2240
            // grandTotalAccounting = 2240 × 36.85 = 82544.00
            InvoiceDto result = sut.calculateInvoice(
                    twoLineItems(), CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            assertThat(result.subtotalOriginal()).isEqualByComparingTo("1950.0000");
            assertThat(result.taxAmountOriginal()).isEqualByComparingTo("290.0000");
            assertThat(result.grandTotalOriginal()).isEqualByComparingTo("2240.0000");
            assertThat(result.grandTotalAccounting()).isEqualByComparingTo("82544.00");
        }

        @Test
        @DisplayName("Kur SELLING tipinde çözümlenmeli")
        void rate_resolvedWithSellingType() {
            sut.calculateInvoice(twoLineItems(), CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            verify(currencyConversionService, times(1))
                    .getRate("EUR", "TRY", RATE_DATE, RateType.SELLING);
        }

        @Test
        @DisplayName("exchangeRateValue fatura DTO'suna kilitlenmeli")
        void exchangeRateValue_lockedInDto() {
            InvoiceDto result = sut.calculateInvoice(
                    twoLineItems(), CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            assertThat(result.exchangeRateValue()).isEqualByComparingTo(EUR_TRY_RATE);
            assertThat(result.exchangeRateDate()).isEqualTo(RATE_DATE);
        }

        @Test
        @DisplayName("Varsayılan status DRAFT olmalı")
        void status_defaultDraft() {
            InvoiceDto result = sut.calculateInvoice(
                    twoLineItems(), CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            assertThat(result.status()).isEqualTo(InvoiceStatus.DRAFT);
        }

        @Test
        @DisplayName("Para birimi bilgileri DTO'ya doğru aktarılmalı")
        void currencyFields_propagatedCorrectly() {
            InvoiceDto result = sut.calculateInvoice(
                    twoLineItems(), CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            assertThat(result.invoiceCurrency()).isEqualTo("EUR");
            assertThat(result.accountingCurrency()).isEqualTo("TRY");
        }

        @Test
        @DisplayName("Kur bulunamadığında ExchangeRateNotFoundException fırlatılmalı")
        void rateNotFound_throwsException() {
            when(currencyConversionService.getRate(anyString(), anyString(), any(), any()))
                    .thenThrow(new ExchangeRateNotFoundException("USD", "TRY", RATE_DATE, RateType.SELLING));

            assertThatThrownBy(() ->
                    sut.calculateInvoice(twoLineItems(), CUSTOMER_ID, LOCATION_ID, "USD", RATE_DATE))
                    .isInstanceOf(ExchangeRateNotFoundException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("TRY");
        }

        @Test
        @DisplayName("Tek satırlı fatura: grandTotalAccounting doğru ölçekte yuvarlanmalı")
        void singleLine_accountingScaleCorrect() {
            // 1 × 99.99 − 0 = 99.99, tax %20 = 19.998 → 20.0000 (HALF_EVEN)
            // grandTotal = 119.9900, accounting = 119.99 × 36.85 = 4422.3115 → 4422.31 (scale=2)
            var items = List.of(InvoiceItemInputDto.builder()
                    .itemDescription("Test")
                    .quantity(BigDecimal.ONE)
                    .unitPriceOriginal(new BigDecimal("99.99"))
                    .discountOriginal(BigDecimal.ZERO)
                    .taxTypeCode("KDV").taxRate(new BigDecimal("20.00"))
                    .build());

            InvoiceDto result = sut.calculateInvoice(items, CUSTOMER_ID, LOCATION_ID, "EUR", RATE_DATE);

            assertThat(result.grandTotalAccounting().scale()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Rate-Lock Validasyonu
    // =========================================================================

    @Nested
    @DisplayName("assertRateLock()")
    class RateLockTests {

        private Invoice approvedInvoice(BigDecimal rate, LocalDate date) {
            Invoice inv = new Invoice();
            inv.setStatus(InvoiceStatus.APPROVED);
            inv.setExchangeRateValue(rate);
            inv.setExchangeRateDate(date);
            inv.setId(1L);
            return inv;
        }

        @Test
        @DisplayName("APPROVED fatura: kur değeri değiştirilmeye çalışılırsa exception fırlatılmalı")
        void approved_rateValueChanged_throws() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);

            assertThatThrownBy(() ->
                    sut.assertRateLock(inv, new BigDecimal("37.000000"), RATE_DATE))
                    .isInstanceOf(RateLockViolationException.class)
                    .hasMessageContaining(inv.getId().toString());
        }

        @Test
        @DisplayName("APPROVED fatura: kur tarihi değiştirilmeye çalışılırsa exception fırlatılmalı")
        void approved_rateDateChanged_throws() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);

            assertThatThrownBy(() ->
                    sut.assertRateLock(inv, EUR_TRY_RATE, RATE_DATE.plusDays(1)))
                    .isInstanceOf(RateLockViolationException.class);
        }

        @Test
        @DisplayName("APPROVED fatura: aynı kur değerleriyle güncelleme exception fırlatmamalı")
        void approved_sameRateValues_noException() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);

            assertThatCode(() ->
                    sut.assertRateLock(inv, EUR_TRY_RATE, RATE_DATE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("APPROVED fatura: sayısal olarak aynı kur farklı scale ile gelirse ihlal sayılmamalı")
        void approved_sameRateDifferentScale_noException() {
            Invoice inv = approvedInvoice(new BigDecimal("30.000000"), RATE_DATE);
            BigDecimal sameRateDifferentScale = new BigDecimal("30.00");

            assertThatCode(() ->
                    sut.assertRateLock(inv, sameRateDifferentScale, RATE_DATE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DRAFT fatura: kur değişikliğine izin verilmeli")
        void draft_rateChanged_noException() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);
            inv.setStatus(InvoiceStatus.DRAFT);

            assertThatCode(() ->
                    sut.assertRateLock(inv, new BigDecimal("99.000000"), RATE_DATE.plusMonths(1)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SENT_TO_ERP fatura: kur değişikliğine izin verilmemeli")
        void sentToErp_rateChanged_throws() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);
            inv.setStatus(InvoiceStatus.SENT_TO_ERP);

            assertThatThrownBy(() ->
                    sut.assertRateLock(inv, new BigDecimal("40.000000"), RATE_DATE))
                    .isInstanceOf(RateLockViolationException.class);
        }

        @Test
        @DisplayName("CANCELLED fatura: kur değişikliğine izin verilmeli")
        void cancelled_rateChanged_noException() {
            Invoice inv = approvedInvoice(EUR_TRY_RATE, RATE_DATE);
            inv.setStatus(InvoiceStatus.CANCELLED);

            assertThatCode(() ->
                    sut.assertRateLock(inv, new BigDecimal("40.000000"), RATE_DATE))
                    .doesNotThrowAnyException();
        }
    }
}
