package com.wms.inbound.service.strategy;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.dto.StorageLocationStatus;
import com.wms.inbound.entity.ReceiptItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ZoneMatchSupport")
class ZoneMatchSupportTest {

    private static final Long WAREHOUSE_ID = 1L;

    private StorageLocationResponse location(String zoneType, String zoneCode) {
        return new StorageLocationResponse(
                1L, 1L, WAREHOUSE_ID,
                zoneType, zoneCode,
                "A-01-01-01", "A", "01", "01", "01",
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, StorageLocationStatus.ACTIVE, true);
    }

    @Nested
    @DisplayName("determineRequiredZoneType()")
    class DetermineRequiredZoneTypeTests {

        @Test
        @DisplayName("null ürün kodu STANDARD döner")
        void nullProductCode_returnsStandard() {
            assertThat(ZoneMatchSupport.determineRequiredZoneType(null)).isEqualTo("STANDARD");
        }

        @ParameterizedTest
        @CsvSource({
                "COLD_MILK, COLD_ROOM",
                "SOGUK_YOGURT, COLD_ROOM",
                "HAZ_CHEMICAL, HAZARDOUS",
                "KIMYASAL_X, HAZARDOUS",
                "BULK_ITEM, BULK",
                "HACIMLI_PALET, BULK",
                "REGULAR_SKU, STANDARD"
        })
        void productCode_mapsToExpectedZone(String productCode, String expectedZone) {
            assertThat(ZoneMatchSupport.determineRequiredZoneType(productCode)).isEqualTo(expectedZone);
        }
    }

    @Nested
    @DisplayName("filterByZone()")
    class FilterByZoneTests {

        @Test
        @DisplayName("soğuk ürün COLD_ZONE kodu ile eşleşir")
        void coldProduct_matchesColdZoneCodeAlias() {
            StorageLocationResponse coldByCode = location("STANDARD", "COLD_ZONE");
            ReceiptItem item = ReceiptItem.builder().productCode("COLD_MILK").build();

            List<StorageLocationResponse> matched =
                    ZoneMatchSupport.filterByZone(List.of(coldByCode), item);

            assertThat(matched).containsExactly(coldByCode);
        }

        @Test
        @DisplayName("tehlikeli ürün HAZMAT zoneType ile eşleşir")
        void hazardousProduct_matchesHazmatZoneType() {
            StorageLocationResponse hazmat = location("HAZMAT", "ZONE-7");
            StorageLocationResponse standard = location("STANDARD", "STD_ZONE");
            ReceiptItem item = ReceiptItem.builder().productCode("HAZ_PAINT").build();

            List<StorageLocationResponse> matched =
                    ZoneMatchSupport.filterByZone(List.of(standard, hazmat), item);

            assertThat(matched).containsExactly(hazmat);
        }

        @Test
        @DisplayName("özel zone bulunamazsa STANDARD lokasyonlara düşer")
        void coldProduct_fallsBackToStandardWhenNoColdZone() {
            StorageLocationResponse standard = location("STANDARD", "STD_ZONE");
            StorageLocationResponse bulk = location("BULK", "BULK_ZONE");
            ReceiptItem item = ReceiptItem.builder().productCode("COLD_MILK").build();

            List<StorageLocationResponse> matched =
                    ZoneMatchSupport.filterByZone(List.of(bulk, standard), item);

            assertThat(matched).containsExactly(standard);
        }

        @Test
        @DisplayName("STANDARD ürün yalnızca STANDARD zone ile eşleşir")
        void standardProduct_doesNotMatchColdZone() {
            StorageLocationResponse cold = location("COLD_ROOM", "COLD");
            StorageLocationResponse standard = location("STANDARD", "STD");
            ReceiptItem item = ReceiptItem.builder().productCode("REGULAR_ITEM").build();

            List<StorageLocationResponse> matched =
                    ZoneMatchSupport.filterByZone(List.of(cold, standard), item);

            assertThat(matched).containsExactly(standard);
        }

        @Test
        @DisplayName("STANDARD ürün için eşleşme yoksa boş liste döner")
        void standardProduct_returnsEmptyWhenNoStandardZone() {
            StorageLocationResponse cold = location("COLD_ROOM", "COLD");
            ReceiptItem item = ReceiptItem.builder().productCode("REGULAR_ITEM").build();

            assertThat(ZoneMatchSupport.filterByZone(List.of(cold), item)).isEmpty();
        }
    }
}
