package com.wms.finance.tax.engine;

import com.wms.finance.tax.audit.entity.TaxCalculationLog;
import com.wms.finance.tax.audit.entity.TaxType;
import com.wms.finance.tax.audit.entity.TransactionType;
import com.wms.finance.tax.audit.repository.AuditTaxTypeRepository;
import com.wms.finance.tax.audit.service.TaxCalculationLogWriter;
import com.wms.finance.tax.engine.factory.TaxStrategyFactory;
import com.wms.finance.tax.engine.model.*;
import com.wms.finance.tax.engine.service.TaxEngineService;
import com.wms.finance.service.TaxResolutionService;
import com.wms.finance.tax.strategy.TaxExclusiveStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxEngineService – Ana Vergi Motoru")
class TaxEngineServiceTest {

    // -----------------------------------------------------------------------
    // Mocks & Stubs
    // -----------------------------------------------------------------------

    @Mock
    private AuditTaxTypeRepository taxTypeRepository;

    @Mock
    private TaxCalculationLogWriter logWriter;

    @Mock
    private TaxResolutionService taxResolutionService;

    @Spy
    private TaxExclusiveStrategy exclusiveStrategy;

    @Mock
    private TaxStrategyFactory strategyFactory;

    @InjectMocks
    private TaxEngineService taxEngineService;

    // -----------------------------------------------------------------------
    // Sabitler
    // -----------------------------------------------------------------------

    private static final String KDV_CODE  = "KDV_20";
    private static final BigDecimal KDV_RATE = new BigDecimal("20");
    private static final Long REF_ID     = 1L;
    private static final Long LINE_ID    = 1L;
    private static final Long LOG_ID     = 1L;

    private TaxType kdvTaxType;
    private TaxCalculationLog savedLog;

    @BeforeEach
    void setUp() {
        kdvTaxType = TaxType.builder()
                .id(1L)
                .code(KDV_CODE)
                .description("KDV %20")
                .defaultRate(KDV_RATE)
                .active(true)
                .build();

        savedLog = TaxCalculationLog.builder()
                .transactionType(TransactionType.INVOICE_LINE)
                .transactionReferenceId(REF_ID)
                .taxType(kdvTaxType)
                .taxRate(KDV_RATE)
                .taxBaseAmount(new BigDecimal("1000.00"))
                .calculatedTaxAmount(new BigDecimal("200.00"))
                .inclusive(false)
                .exempt(false)
                .calculationSource("TAX_ENGINE_V1")
                .build();
        // Reflection ile ID set et (Long generate edilmiş gibi simüle et)
        setLogId(savedLog, LOG_ID);
    }

    // -----------------------------------------------------------------------
    // calculate() — Temel Senaryo
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculate() — Temel Hesaplama")
    class CalculateTests {

        @Test
        @DisplayName("Tek kalem, KDV %20 exclusive: 1000 TL → 200 TL vergi, 1200 TL toplam")
        void singleItemSingleRule_exclusiveKdv20() {
            // Arrange
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            stubLogSave(savedLog);

            TaxEngineContext context = buildContext(
                    List.of(item(LINE_ID, "1000.00")),
                    List.of(TaxRule.of(KDV_CODE, TaxStrategyType.EXCLUSIVE, "TAX_ENGINE_V1")),
                    false
            );

            // Act
            List<TaxCalculationResultDto> results = taxEngineService.calculate(context);

            // Assert
            assertThat(results).hasSize(1);
            TaxCalculationResultDto result = results.getFirst();
            assertThat(result.lineId()).isEqualTo(LINE_ID);
            assertThat(result.taxTypeCode()).isEqualTo(KDV_CODE);
            assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.taxAmount()).isEqualByComparingTo("200.00");
            assertThat(result.grandTotal()).isEqualByComparingTo("1200.00");
            assertThat(result.exempt()).isFalse();
            assertThat(result.logId()).isEqualTo(LOG_ID);
        }

        @Test
        @DisplayName("İki kalem, iki kural → 4 sonuç üretilmeli (N×M kartezyen çarpım)")
        void twoItemsTwoRules_producesCartesianResults() {
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            stubLogSave(savedLog);

            TaxEngineContext context = buildContext(
                    List.of(item(1L, "500.00"), item(1L, "300.00")),
                    List.of(
                            TaxRule.of(KDV_CODE, TaxStrategyType.EXCLUSIVE, "TAX_ENGINE_V1"),
                            TaxRule.of(KDV_CODE, TaxStrategyType.EXCLUSIVE, "TAX_ENGINE_V1")
                    ),
                    false
            );

            List<TaxCalculationResultDto> results = taxEngineService.calculate(context);

            assertThat(results).hasSize(4);
        }

        @Test
        @DisplayName("Log repository kalem×kural sayısı kadar save() çağrılmalı")
        void logRepository_calledForEachLineRulePair() {
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            stubLogSave(savedLog);

            TaxEngineContext context = buildContext(
                    List.of(item(LINE_ID, "1000.00")),
                    List.of(TaxRule.of(KDV_CODE, TaxStrategyType.EXCLUSIVE, "TAX_ENGINE_V1")),
                    false
            );

            taxEngineService.calculate(context);

            verify(logWriter, times(1)).persist(
                    any(), any(), any(), any(), any(), any(),
                    anyBoolean(), anyBoolean(), any(), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — Muafiyet Senaryosu
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculate() — Muafiyet Yönetimi")
    class ExemptionTests {

        @Test
        @DisplayName("Muaf kural: vergi tutarı 0.00, exemptionCode log kaydına iletilmeli")
        void exemptRule_taxAmountZeroAndExemptionCodePersisted() {
            // Arrange
            String exemptionCode = "IHRACAT_MUAF";
            TaxCalculationLog exemptLog = TaxCalculationLog.builder()
                    .transactionType(TransactionType.INVOICE_LINE)
                    .transactionReferenceId(REF_ID)
                    .taxType(kdvTaxType)
                    .taxRate(KDV_RATE)
                    .taxBaseAmount(new BigDecimal("1000.00"))
                    .calculatedTaxAmount(BigDecimal.ZERO)
                    .inclusive(false)
                    .exempt(true)
                    .exemptionCode(exemptionCode)
                    .calculationSource("TAX_ENGINE_V1")
                    .build();
            setLogId(exemptLog, LOG_ID);

            stubTaxType(KDV_CODE, kdvTaxType);
            stubLogSave(exemptLog);

            TaxEngineContext context = buildContext(
                    List.of(item(LINE_ID, "1000.00")),
                    List.of(TaxRule.exempt(KDV_CODE, TaxStrategyType.EXCLUSIVE, exemptionCode, "TAX_ENGINE_V1")),
                    false
            );

            // Act
            List<TaxCalculationResultDto> results = taxEngineService.calculate(context);

            // Assert
            TaxCalculationResultDto result = results.getFirst();
            assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
            assertThat(result.exempt()).isTrue();
            assertThat(result.exemptionCode()).isEqualTo(exemptionCode);
            assertThat(result.grandTotal()).isEqualByComparingTo("1000.00"); // vergi eklenmedi

            // Strateji hiç çağrılmamalı — muafiyette hesaplama yapılmaz
            verify(strategyFactory, never()).resolve(any());
        }

        @Test
        @DisplayName("Log'a yazılan kayıt: exempt=true, calculatedTaxAmount=0, exemptionCode dolu olmalı")
        void persistLog_exemptFieldsCorrect() {
            String exemptionCode = "OIB_MUAF_2024";
            stubTaxType(KDV_CODE, kdvTaxType);
            ArgumentCaptor<TaxCalculationLog> captor = ArgumentCaptor.forClass(TaxCalculationLog.class);
            when(logWriter.persist(
                    any(), any(), any(), any(), any(), any(),
                    anyBoolean(), anyBoolean(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        TaxCalculationLog log = TaxCalculationLog.builder()
                                .transactionType(inv.getArgument(0))
                                .transactionReferenceId(inv.getArgument(1))
                                .taxType(inv.getArgument(2))
                                .taxRate(inv.getArgument(3))
                                .taxBaseAmount(inv.getArgument(4))
                                .calculatedTaxAmount(inv.getArgument(5))
                                .inclusive(inv.getArgument(6))
                                .exempt(inv.getArgument(7))
                                .exemptionCode(inv.getArgument(8))
                                .calculationSource(inv.getArgument(9))
                                .calculationDate(inv.getArgument(10))
                                .build();
                        setLogId(log, LOG_ID);
                        return log;
                    });

            TaxEngineContext context = buildContext(
                    List.of(item(LINE_ID, "500.00")),
                    List.of(TaxRule.exempt(KDV_CODE, TaxStrategyType.EXCLUSIVE, exemptionCode, "TAX_ENGINE_V1")),
                    false
            );

            taxEngineService.calculate(context);

            verify(logWriter).persist(
                    eq(TransactionType.INVOICE_LINE),
                    eq(REF_ID),
                    eq(kdvTaxType),
                    eq(KDV_RATE),
                    eq(new BigDecimal("500.00")),
                    eq(BigDecimal.ZERO),
                    eq(false),
                    eq(true),
                    eq(exemptionCode),
                    eq("TAX_ENGINE_V1"),
                    any());
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — Oran Çözümleme
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculate() — TaxResolutionService entegrasyonu")
    class RateResolutionTests {

        @Test
        @DisplayName("Context'te countryId varsa TaxResolutionService oranı kullanılır")
        void resolvedRate_usedWhenContextHasCountry() {
            Long countryId = 1L;
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            stubLogSave(savedLog);

            when(taxResolutionService.resolveTaxRate(
                    eq("KDV"), any(), any()))
                    .thenReturn(new BigDecimal("18"));

            TaxEngineContext context = new TaxEngineContext(
                    TransactionType.INVOICE_LINE,
                    REF_ID,
                    List.of(item(LINE_ID, "1000.00")),
                    List.of(TaxRule.of(KDV_CODE, "KDV", TaxStrategyType.EXCLUSIVE, "TAX_ENGINE_V1")),
                    false,
                    countryId, null, null, null, null,
                    LocalDate.of(2026, 7, 4));

            var result = taxEngineService.calculate(context).getFirst();

            assertThat(result.taxAmount()).isEqualByComparingTo("180.00");
            verify(taxResolutionService).resolveTaxRate(eq("KDV"), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // calculateExchangeDifferenceTax()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculateExchangeDifferenceTax() — Kur Farkı Vergisi")
    class ForexTaxTests {

        private final Long locationId = 1L;
        private final LocalDate tradeDate = LocalDate.of(2026, 7, 4);

        @Test
        @DisplayName("Pozitif kur farkı: %20 KDV uygulanmalı")
        void positiveExchangeDiff_taxApplied() {
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            stubLogSave(savedLog);

            TaxCalculationResultDto result = taxEngineService.calculateExchangeDifferenceTax(
                    new BigDecimal("1000.00"), KDV_CODE, tradeDate, locationId);

            assertThat(result.taxAmount()).isEqualByComparingTo("200.00");
            assertThat(result.grandTotal()).isEqualByComparingTo("1200.00");
            assertThat(result.exempt()).isFalse();
            assertThat(result.logId()).isNotNull();
        }

        @Test
        @DisplayName("Negatif kur farkı (kayıp): vergi hesaplanmaz, logId null döner")
        void negativeExchangeDiff_noTax() {
            TaxCalculationResultDto result = taxEngineService.calculateExchangeDifferenceTax(
                    new BigDecimal("-500.00"), KDV_CODE, tradeDate, locationId);

            assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
            assertThat(result.logId()).isNull();
            verifyNoInteractions(taxTypeRepository);
            verifyNoInteractions(logWriter);
        }

        @Test
        @DisplayName("Sıfır kur farkı: vergi hesaplanmaz")
        void zeroExchangeDiff_noTax() {
            TaxCalculationResultDto result = taxEngineService.calculateExchangeDifferenceTax(
                    BigDecimal.ZERO, KDV_CODE, tradeDate, locationId);

            assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Log kaydı TRANSACTION_FEE tipiyle yazılmalı")
        void forexLog_transactionTypeFee() {
            stubTaxType(KDV_CODE, kdvTaxType);
            stubStrategy(TaxStrategyType.EXCLUSIVE, exclusiveStrategy);
            when(logWriter.persist(
                    any(), any(), any(), any(), any(), any(),
                    anyBoolean(), anyBoolean(), any(), any(), any()))
                    .thenReturn(savedLog);

            taxEngineService.calculateExchangeDifferenceTax(
                    new BigDecimal("2000.00"), KDV_CODE, tradeDate, locationId);

            verify(logWriter).persist(
                    eq(TransactionType.TRANSACTION_FEE),
                    eq(locationId),
                    any(), any(), any(), any(),
                    eq(false), eq(false), isNull(), contains("FOREX"), any());
        }

        @Test
        @DisplayName("Null exchangeDifferenceAmount → IllegalArgumentException")
        void nullAmount_throws() {
            assertThatThrownBy(() -> taxEngineService.calculateExchangeDifferenceTax(
                    null, KDV_CODE, tradeDate, locationId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Bilinmeyen vergi tipi kodu → IllegalArgumentException")
        void unknownTaxTypeCode_throws() {
            when(taxTypeRepository.findByCodeAndActiveTrue("UNKNOWN"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxEngineService.calculateExchangeDifferenceTax(
                    new BigDecimal("100.00"), "UNKNOWN", tradeDate, locationId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Aktif vergi tipi bulunamadı");
        }
    }

    // -----------------------------------------------------------------------
    // Validasyon Testleri
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Context Validasyonu")
    class ValidationTests {

        @Test
        @DisplayName("Boş kalem listesi → IllegalArgumentException")
        void emptyItems_throws() {
            assertThatThrownBy(() -> buildContext(
                    List.of(),
                    List.of(TaxRule.of(KDV_CODE, TaxStrategyType.EXCLUSIVE, "SRC")),
                    false)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Boş kural listesi → IllegalArgumentException")
        void emptyRules_throws() {
            assertThatThrownBy(() -> buildContext(
                    List.of(item(LINE_ID, "100.00")),
                    List.of(),
                    false)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // Test Yardımcı Metotları
    // =========================================================================

    private TaxEngineContext buildContext(List<TaxLineItem> items,
                                          List<TaxRule> rules,
                                          boolean inclusive) {
        return TaxEngineContext.of(
                TransactionType.INVOICE_LINE,
                REF_ID,
                items,
                rules,
                inclusive
        );
    }

    private TaxLineItem item(Long id, String amount) {
        return new TaxLineItem(id, "Test kalem", new BigDecimal(amount));
    }

    private void stubTaxType(String code, TaxType taxType) {
        when(taxTypeRepository.findByCodeAndActiveTrue(code)).thenReturn(Optional.of(taxType));
    }

    private void stubStrategy(TaxStrategyType type,
                               com.wms.finance.tax.strategy.TaxCalculationStrategy strategy) {
        when(strategyFactory.resolve(type)).thenReturn(strategy);
    }

    private void stubLogSave(TaxCalculationLog log) {
        when(logWriter.persist(
                any(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(log);
    }

    /** Test ortamında entity ID'sini reflection ile set eder. */
    private void setLogId(TaxCalculationLog log, Long id) {
        try {
            var field = TaxCalculationLog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(log, id);
        } catch (Exception e) {
            throw new RuntimeException("Log ID set edilemedi", e);
        }
    }
}
