package com.wms.inbound.service.strategy;

import com.wms.inbound.entity.ReceiptItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CapacityMatchStrategy} parametre doğrulama testleri. Saf birim testi — Docker gerektirmez.
 */
class CapacityMatchStrategyTest {

    private final CapacityMatchStrategy strategy = new CapacityMatchStrategy();

    private static ReceiptItem itemWithQuantity(BigDecimal quantity) {
        return ReceiptItem.builder().productCode("STD_ITEM_01").quantity(quantity).build();
    }

    @Test
    @DisplayName("Aday listesi null ise NullPointerException atar")
    void nullCandidates_throws() {
        assertThatThrownBy(() -> strategy.filterLocations(null, itemWithQuantity(BigDecimal.ONE)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Kalem null ise NullPointerException atar")
    void nullItem_throws() {
        assertThatThrownBy(() -> strategy.filterLocations(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Miktar sıfır ise boş liste yerine IllegalArgumentException atar")
    void zeroQuantity_throws() {
        assertThatThrownBy(() -> strategy.filterLocations(List.of(), itemWithQuantity(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Miktar null ise IllegalArgumentException atar")
    void nullQuantity_throws() {
        assertThatThrownBy(() -> strategy.filterLocations(List.of(), itemWithQuantity(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Geçerli kalem ve boş aday listesi — boş liste döner (hata değil)")
    void emptyCandidates_returnsEmpty() {
        assertThat(strategy.filterLocations(List.of(), itemWithQuantity(BigDecimal.ONE))).isEmpty();
    }
}
