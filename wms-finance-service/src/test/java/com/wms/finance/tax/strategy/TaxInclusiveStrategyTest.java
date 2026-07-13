package com.wms.finance.tax.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TaxInclusiveStrategy – KDV Dahil Hesaplama")
class TaxInclusiveStrategyTest {

    private TaxInclusiveStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TaxInclusiveStrategy();
    }

    @Test
    @DisplayName("KDV %20 dahil: 1200 TL brüt → 1000 TL matrah, 200 TL vergi")
    void standardKdv20Inclusive() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("1200.00"), new BigDecimal("20"));

        assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("200.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("KDV %18 dahil: 1180 TL brüt → 1000 TL matrah, 180 TL vergi")
    void standardKdv18Inclusive() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("1180.00"), new BigDecimal("18"));

        assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("Sıfır oran: matrah brüt tutara eşit, vergi sıfır")
    void zeroRate() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("500.00"), BigDecimal.ZERO);

        assertThat(result.baseAmount()).isEqualByComparingTo("500.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Sıfır brüt tutar: tüm alanlar sıfır")
    void zeroGrossAmount() {
        TaxCalculationResult result = strategy.calculateTax(
                BigDecimal.ZERO, new BigDecimal("20"));

        assertThat(result.baseAmount()).isEqualByComparingTo("0.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "Brüt={0}, Oran={1} → Matrah≈{2}, Vergi≈{3}")
    @DisplayName("Parametrik – çeşitli brüt/oran kombinasyonları")
    @CsvSource({
            "110.00, 10, 100.00, 10.00",
            "118.00, 18, 100.00, 18.00",
            "1000.00, 25, 800.00, 200.00",
    })
    void parametricTests(String gross, String rate, String expectedBase, String expectedTax) {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal(gross), new BigDecimal(rate));

        assertThat(result.baseAmount()).isEqualByComparingTo(new BigDecimal(expectedBase));
        assertThat(result.taxAmount()).isEqualByComparingTo(new BigDecimal(expectedTax));
    }

    @Test
    @DisplayName("Exclusive → Inclusive tutarlılığı: matrahtan hesaplanan toplam, dahil stratejiye eşit olmalı")
    void exclusiveInclusiveConsistency() {
        BigDecimal base = new BigDecimal("1000.00");
        BigDecimal rate = new BigDecimal("20");

        TaxExclusiveStrategy exclusive = new TaxExclusiveStrategy();
        TaxCalculationResult exclusiveResult = exclusive.calculateTax(base, rate);

        // Exclusive sonucunun grandTotal'ını Inclusive'e ver
        TaxCalculationResult inclusiveResult = strategy.calculateTax(
                exclusiveResult.grandTotal(), rate);

        assertThat(inclusiveResult.baseAmount()).isEqualByComparingTo(base);
        assertThat(inclusiveResult.taxAmount()).isEqualByComparingTo(exclusiveResult.taxAmount());
    }

    @Test
    @DisplayName("Negatif brüt tutar → IllegalArgumentException")
    void negativeGrossAmount() {
        assertThatThrownBy(() -> strategy.calculateTax(new BigDecimal("-100"), new BigDecimal("18")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
