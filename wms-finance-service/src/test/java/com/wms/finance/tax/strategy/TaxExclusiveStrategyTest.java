package com.wms.finance.tax.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TaxExclusiveStrategy – KDV Hariç Hesaplama")
class TaxExclusiveStrategyTest {

    private TaxExclusiveStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TaxExclusiveStrategy();
    }

    @Test
    @DisplayName("Standart KDV %20: 1000 TL matrah → 200 TL vergi, 1200 TL toplam")
    void standardKdv20() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("1000.00"), new BigDecimal("20"));

        assertThat(result.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("200.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Standart KDV %18: 1000 TL matrah → 180 TL vergi, 1180 TL toplam")
    void standardKdv18() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("1000.00"), new BigDecimal("18"));

        assertThat(result.taxAmount()).isEqualByComparingTo("180.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("1180.00");
    }

    @Test
    @DisplayName("Sıfır vergi oranı: vergi tutarı sıfır, toplam matrah'a eşit")
    void zeroRate() {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal("500.00"), BigDecimal.ZERO);

        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Sıfır matrah: tüm alanlar sıfır")
    void zeroBaseAmount() {
        TaxCalculationResult result = strategy.calculateTax(
                BigDecimal.ZERO, new BigDecimal("18"));

        assertThat(result.baseAmount()).isEqualByComparingTo("0.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "Matrah={0}, Oran={1} → Vergi={2}, Toplam={3}")
    @DisplayName("Parametrik – çeşitli matrah/oran kombinasyonları")
    @CsvSource({
            "100.00,  10,  10.00, 110.00",
            "333.33,   9,  30.00, 363.33",   // Banker's Rounding: 333.33 * 0.09 = 29.9997 → 30.00
            "999.99,  20, 200.00, 1199.99",
            "0.01,    20,   0.00,   0.01",   // çok küçük tutar
            "1000000, 18, 180000.00, 1180000.00"
    })
    void parametricTests(String base, String rate, String expectedTax, String expectedTotal) {
        TaxCalculationResult result = strategy.calculateTax(
                new BigDecimal(base), new BigDecimal(rate));

        assertThat(result.taxAmount()).isEqualByComparingTo(new BigDecimal(expectedTax));
        assertThat(result.grandTotal()).isEqualByComparingTo(new BigDecimal(expectedTotal));
    }

    @Test
    @DisplayName("Negatif matrah → IllegalArgumentException")
    void negativeBaseAmount() {
        assertThatThrownBy(() -> strategy.calculateTax(new BigDecimal("-1"), new BigDecimal("18")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negatif");
    }

    @Test
    @DisplayName("Negatif oran → IllegalArgumentException")
    void negativeRate() {
        assertThatThrownBy(() -> strategy.calculateTax(new BigDecimal("100"), new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("100'den büyük oran → IllegalArgumentException")
    void rateExceeds100() {
        assertThatThrownBy(() -> strategy.calculateTax(new BigDecimal("100"), new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Null parametreler → IllegalArgumentException")
    void nullParameters() {
        assertThatThrownBy(() -> strategy.calculateTax(null, new BigDecimal("18")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.calculateTax(new BigDecimal("100"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
