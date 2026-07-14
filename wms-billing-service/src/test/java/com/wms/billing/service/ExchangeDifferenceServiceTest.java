package com.wms.billing.service;

import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.repository.ExchangeDifferenceLogRepository;
import com.wms.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExchangeDifferenceService")
class ExchangeDifferenceServiceTest {

    @Mock private InvoiceRepository              invoiceRepository;
    @Mock private ExchangeDifferenceLogRepository logRepository;
    @Mock private TaxLookupService               taxLookupService;

    @InjectMocks
    private ExchangeDifferenceService sut;

    private static final Long INVOICE_ID  = 1L;

    /** Fatura kesilirken kilitlenen kur: 1 EUR = 36.85 TRY */
    private static final BigDecimal LOCKED_RATE = new BigDecimal("36.850000");

    @BeforeEach
    void initDefaults() {
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "exchangeDifferenceTaxType", "KDV");
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "defaultCountryId", 1L);
        when(taxLookupService.calculateExchangeDifferenceTax(any(), any(), any(), any(), any()))
                .thenReturn(new TaxLookupService.TaxLineResult(
                        new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("120"),
                        "KDV", new BigDecimal("20"), false));
    }

    private Invoice buildInvoice(BigDecimal lockedRate) {
        Invoice inv = new Invoice();
        inv.setId(INVOICE_ID);
        inv.setLocationId(1L);
        inv.setExchangeRateValue(lockedRate);
        inv.setInvoiceCurrency("EUR");
        inv.setAccountingCurrency("TRY");
        inv.setStatus(InvoiceStatus.APPROVED);
        return inv;
    }

    @BeforeEach
    void mockSave() {
        // save() gerçek log'u döndürsün
        when(logRepository.save(any(ExchangeDifferenceLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // Artı Kur Farkı (Kazanç)
    // =========================================================================

    @Nested
    @DisplayName("Kur Kazancı — rateAtPayment > lockedRate")
    class ExchangeGainTests {

        @Test
        @DisplayName("Kur yükseldi: exchangeDifferenceAmount pozitif olmalı")
        void rateIncreased_positiveAmount() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            // Ödeme kurunun 1 TRY yükselmesi → kazanç
            BigDecimal rateAtPayment   = new BigDecimal("37.850000"); // +1.00
            BigDecimal paidAmount      = new BigDecimal("1000.0000"); // 1000 EUR

            // rateDiff = 37.85 − 36.85 = 1.00
            // diffAmount = 1000 × 1.00 = 1000.00
            ExchangeDifferenceLog result = sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, paidAmount, rateAtPayment);

            assertThat(result.getExchangeDifferenceAmount())
                    .isEqualByComparingTo("1000.0000");
            assertThat(result.getActionTaken()).isEqualTo("EXCHANGE_GAIN");
        }

        @Test
        @DisplayName("Küçük kur artışı: hassasiyet korunmalı (scale=4)")
        void smallRateIncrease_precisionPreserved() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            BigDecimal rateAtPayment = new BigDecimal("36.852500"); // +0.0025
            BigDecimal paidAmount    = new BigDecimal("500.0000");

            // rateDiff = 0.0025, diffAmount = 500 × 0.0025 = 1.2500
            ExchangeDifferenceLog result = sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, paidAmount, rateAtPayment);

            assertThat(result.getExchangeDifferenceAmount())
                    .isEqualByComparingTo("1.2500");
            assertThat(result.getExchangeDifferenceAmount().scale()).isEqualTo(4);
        }
    }

    // =========================================================================
    // Eksi Kur Farkı (Kayıp)
    // =========================================================================

    @Nested
    @DisplayName("Kur Kaybı — rateAtPayment < lockedRate")
    class ExchangeLossTests {

        @Test
        @DisplayName("Kur düştü: exchangeDifferenceAmount negatif olmalı")
        void rateDecreased_negativeAmount() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            BigDecimal rateAtPayment = new BigDecimal("35.850000"); // -1.00
            BigDecimal paidAmount    = new BigDecimal("2000.0000");

            // rateDiff = 35.85 − 36.85 = -1.00
            // diffAmount = 2000 × (-1.00) = -2000.00
            ExchangeDifferenceLog result = sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, paidAmount, rateAtPayment);

            assertThat(result.getExchangeDifferenceAmount())
                    .isEqualByComparingTo("-2000.0000");
            assertThat(result.getActionTaken()).isEqualTo("EXCHANGE_LOSS");
        }

        @Test
        @DisplayName("Büyük kur düşüşü: tutar ve aksiyon etiketi doğru olmalı")
        void largeRateDrop_correctAmountAndAction() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            BigDecimal rateAtPayment = new BigDecimal("30.000000"); // -6.85
            BigDecimal paidAmount    = new BigDecimal("100.0000");

            // diffAmount = 100 × (30 − 36.85) = 100 × (-6.85) = -685.0000
            ExchangeDifferenceLog result = sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, paidAmount, rateAtPayment);

            assertThat(result.getExchangeDifferenceAmount())
                    .isEqualByComparingTo("-685.0000");
            assertThat(result.getActionTaken()).isEqualTo("EXCHANGE_LOSS");
        }
    }

    // =========================================================================
    // Kur Farkı Yok
    // =========================================================================

    @Nested
    @DisplayName("Kur Farkı Yok — rateAtPayment == lockedRate")
    class NoDifferenceTests {

        @Test
        @DisplayName("Eşit kurlar: amount sıfır, action NO_DIFFERENCE olmalı")
        void sameRate_zeroDifference() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            ExchangeDifferenceLog result = sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, new BigDecimal("500.0000"), LOCKED_RATE);

            assertThat(result.getExchangeDifferenceAmount())
                    .isEqualByComparingTo("0.0000");
            assertThat(result.getActionTaken()).isEqualTo("NO_DIFFERENCE");
        }
    }

    // =========================================================================
    // Persist & Alan Kontrolü
    // =========================================================================

    @Nested
    @DisplayName("Persist ve Alan Doğrulama")
    class PersistTests {

        @Test
        @DisplayName("Log kaydı bir kez persist edilmeli")
        void log_savedExactlyOnce() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, new BigDecimal("100.0000"), new BigDecimal("37.000000"));

            verify(logRepository, times(1)).save(any(ExchangeDifferenceLog.class));
        }

        @Test
        @DisplayName("Log kaydı doğru alanlarla oluşturulmalı")
        void log_fieldsCorrectlyPopulated() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            BigDecimal rateAtPayment = new BigDecimal("38.000000");
            BigDecimal paidAmount    = new BigDecimal("250.0000");

            ArgumentCaptor<ExchangeDifferenceLog> captor =
                    ArgumentCaptor.forClass(ExchangeDifferenceLog.class);

            sut.calculateAndLogExchangeDifference(INVOICE_ID, paidAmount, rateAtPayment);

            verify(logRepository).save(captor.capture());
            ExchangeDifferenceLog captured = captor.getValue();

            assertThat(captured.getInvoice().getId()).isEqualTo(INVOICE_ID);
            assertThat(captured.getOriginalPaidAmount()).isEqualByComparingTo(paidAmount);
            assertThat(captured.getRateAtPayment()).isEqualByComparingTo(rateAtPayment);
            assertThat(captured.getCalculationDate()).isNotNull();
            assertThat(captured.getActionTaken()).isEqualTo("EXCHANGE_GAIN");
            // diffAmount = 250 × (38 − 36.85) = 250 × 1.15 = 287.5000
            assertThat(captured.getExchangeDifferenceAmount()).isEqualByComparingTo("287.5000");
        }

        @Test
        @DisplayName("rateAtPayment scale=6 ile persist edilmeli")
        void rateAtPayment_persistedWithScale6() {
            when(invoiceRepository.findById(INVOICE_ID))
                    .thenReturn(Optional.of(buildInvoice(LOCKED_RATE)));

            ArgumentCaptor<ExchangeDifferenceLog> captor =
                    ArgumentCaptor.forClass(ExchangeDifferenceLog.class);

            sut.calculateAndLogExchangeDifference(
                    INVOICE_ID, new BigDecimal("100.0000"), new BigDecimal("37.5"));

            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getRateAtPayment().scale()).isEqualTo(6);
        }
    }

    // =========================================================================
    // Hata Senaryoları
    // =========================================================================

    @Nested
    @DisplayName("Hata Senaryoları")
    class ErrorTests {

        @Test
        @DisplayName("Fatura bulunamazsa InvoiceNotFoundException fırlatılmalı")
        void invoiceNotFound_throwsException() {
            when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sut.calculateAndLogExchangeDifference(
                            INVOICE_ID, new BigDecimal("100.0000"), new BigDecimal("37.0")))
                    .isInstanceOf(InvoiceNotFoundException.class)
                    .hasMessageContaining(INVOICE_ID.toString());
        }

        @Test
        @DisplayName("Fatura bulunamazsa log kayıt denemesi yapılmamalı")
        void invoiceNotFound_noLogSaveAttempted() {
            when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sut.calculateAndLogExchangeDifference(
                            INVOICE_ID, new BigDecimal("100.0000"), new BigDecimal("37.0")))
                    .isInstanceOf(InvoiceNotFoundException.class);

            verifyNoInteractions(logRepository);
        }
    }
}
