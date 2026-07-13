package com.wms.finance.tax.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CompoundTaxStrategy – Katmanlı Vergi Hesaplama")
class CompoundTaxStrategyTest {

    private final TaxExclusiveStrategy exclusive = new TaxExclusiveStrategy();

    // -----------------------------------------------------------------------
    // Temel Senaryo: ÖTV → KDV
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ÖTV %25 → KDV %20: 1000 TL → ÖTV:250 + KDV:250 = 500 TL toplam vergi, 1500 TL genel toplam")
    void otvThenKdv() {
        // Katman 1: ÖTV %25 (exclusive) — matrah 1000, vergi 250, ara toplam 1250
        // Katman 2: KDV %20 (exclusive) — matrah 1250, vergi 250, genel toplam 1500
        CompoundTaxStrategy compound = new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("25")),
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("20"))
        ));

        TaxCalculationResult result = compound.calculateTax(
                new BigDecimal("1000.00"), BigDecimal.ZERO /* katman oranları kullanılır */);

        assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("500.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("1500.00");
    }

    // -----------------------------------------------------------------------
    // Tek Katman: CompoundTaxStrategy bir TaxExclusiveStrategy gibi davranmalı
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Tek katman: CompoundTaxStrategy, TaxExclusiveStrategy ile aynı sonucu vermeli")
    void singleLayerMatchesExclusive() {
        BigDecimal base = new BigDecimal("2000.00");
        BigDecimal rate = new BigDecimal("18");

        CompoundTaxStrategy compound = new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusive, rate)
        ));

        TaxCalculationResult compoundResult = compound.calculateTax(base, BigDecimal.ZERO);
        TaxCalculationResult exclusiveResult = exclusive.calculateTax(base, rate);

        assertThat(compoundResult.taxAmount()).isEqualByComparingTo(exclusiveResult.taxAmount());
        assertThat(compoundResult.grandTotal()).isEqualByComparingTo(exclusiveResult.grandTotal());
    }

    // -----------------------------------------------------------------------
    // Üç Katman Senaryosu
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Üç katman: %10 → %20 → %5 kümülatif vergi doğruluğu")
    void threeLayerCumulative() {
        // 1000 * 1.10 = 1100  → vergi1 = 100
        // 1100 * 1.20 = 1320  → vergi2 = 220
        // 1320 * 1.05 = 1386  → vergi3 = 66
        // Toplam vergi = 100 + 220 + 66 = 386
        // Grand total  = 1000 + 386 = 1386
        CompoundTaxStrategy compound = new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("10")),
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("20")),
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("5"))
        ));

        TaxCalculationResult result = compound.calculateTax(
                new BigDecimal("1000.00"), BigDecimal.ZERO);

        assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("386.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("1386.00");
    }

    // -----------------------------------------------------------------------
    // Validasyon
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Boş katman listesi → IllegalArgumentException")
    void emptyLayerList() {
        assertThatThrownBy(() -> new CompoundTaxStrategy(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("En az bir");
    }

    @Test
    @DisplayName("Null katman listesi → IllegalArgumentException")
    void nullLayerList() {
        assertThatThrownBy(() -> new CompoundTaxStrategy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Negatif başlangıç matrahı → IllegalArgumentException")
    void negativeBaseAmount() {
        CompoundTaxStrategy compound = new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("18"))
        ));

        assertThatThrownBy(() -> compound.calculateTax(new BigDecimal("-100"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TaxLayer: null strateji → IllegalArgumentException")
    void nullStrategyInLayer() {
        assertThatThrownBy(() -> new CompoundTaxStrategy.TaxLayer(null, new BigDecimal("18")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strateji");
    }

    // -----------------------------------------------------------------------
    // Yuvarlama (Banker's Rounding)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Banker's Rounding: virgüllü tutar hesabında yuvarlama sapması olmamalı")
    void bankersRoundingPrecision() {
        // 333.33 * 0.18 = 59.9994 → HALF_EVEN → 60.00
        CompoundTaxStrategy compound = new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusive, new BigDecimal("18"))
        ));

        TaxCalculationResult result = compound.calculateTax(
                new BigDecimal("333.33"), BigDecimal.ZERO);

        // baseAmount + taxAmount == grandTotal olmalı (tutarlılık kaydı)
        assertThat(result.baseAmount().add(result.taxAmount()))
                .isEqualByComparingTo(result.grandTotal());
    }
}
