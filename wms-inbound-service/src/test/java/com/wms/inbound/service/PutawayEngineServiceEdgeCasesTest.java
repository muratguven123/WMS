package com.wms.inbound.service;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.dto.StorageLocationStatus;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderItem;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.integration.CoreServiceClient;
import com.wms.inbound.service.strategy.CapacityMatchStrategy;
import com.wms.inbound.service.strategy.ZoneMatchStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link PutawayEngineService} kenar durum testleri — mevcut {@code PutawayEngineServiceTest}'i
 * tamamlar. Özellikle <b>depo izolasyon filtresi</b> (hedef depoya ait olmayan gözlerin elenmesi)
 * ve tam-detaylı öneri dönüşü ({@code findRecommendedLocation}) doğrulanır. Gerçek strateji
 * bean'leriyle koşar (davranış üretimdekiyle aynı). Saf Mockito — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class PutawayEngineServiceEdgeCasesTest {

    @Mock
    private CoreServiceClient coreServiceClient;

    private PutawayEngineService putawayEngineService;

    @BeforeEach
    void setUp() {
        putawayEngineService = new PutawayEngineService(
                coreServiceClient, new ZoneMatchStrategy(), new CapacityMatchStrategy());
    }

    private static StorageLocationResponse standardLocation(Long id, Long warehouseLocationId, String address) {
        return new StorageLocationResponse(
                id, 1L, warehouseLocationId, "STD_ZONE", "STANDARD",
                address, "A", "01", "01", "01",
                new BigDecimal("10.0000"), new BigDecimal("100.0000"),
                new BigDecimal("1.0000"), new BigDecimal("10.0000"),
                new BigDecimal("10.00"), StorageLocationStatus.ACTIVE, true);
    }

    private static ReceiptItem standardReceiptItem() {
        InboundOrder order = new InboundOrder();
        InboundOrderItem orderItem = InboundOrderItem.builder()
                .productCode("STD_ITEM_01")
                .unitVolume(new BigDecimal("0.5000"))
                .unitWeight(new BigDecimal("5.0000"))
                .build();
        order.setItems(List.of(orderItem));

        Receipt receipt = Receipt.builder().inboundOrder(order).build();
        return ReceiptItem.builder()
                .receipt(receipt)
                .productCode("STD_ITEM_01")
                .quantity(new BigDecimal("2.0000"))
                .build();
    }

    @Test
    @DisplayName("Hedef depoda hiç göz yoksa (tüm adaylar başka depoda) — boş döner")
    void noLocationInTargetWarehouse_returnsEmpty() {
        // Aday gözlerin tümü warehouseLocationId=2'de; sorgu warehouse=1 için yapılıyor.
        when(coreServiceClient.getActiveLocations()).thenReturn(List.of(
                standardLocation(10L, 2L, "B-01-01-01"),
                standardLocation(11L, 2L, "B-01-01-02")));

        Optional<Long> result = putawayEngineService.findPutawayLocation(standardReceiptItem(), 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findRecommendedLocation — id değil, tam StorageLocationResponse döner")
    void findRecommendedLocation_returnsFullDetail() {
        StorageLocationResponse target = standardLocation(42L, 1L, "A-09-09-09");
        when(coreServiceClient.getActiveLocations()).thenReturn(List.of(target));

        Optional<StorageLocationResponse> result =
                putawayEngineService.findRecommendedLocation(standardReceiptItem(), 1L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(42L);
        assertThat(result.get().addressCode()).isEqualTo("A-09-09-09");
        assertThat(result.get().zoneType()).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("findPutawayLocation — önerilen gözün yalnız id'sini döner")
    void findPutawayLocation_mapsToId() {
        StorageLocationResponse target = standardLocation(77L, 1L, "A-01-01-01");
        when(coreServiceClient.getActiveLocations()).thenReturn(List.of(target));

        Optional<Long> result = putawayEngineService.findPutawayLocation(standardReceiptItem(), 1L);

        assertThat(result).contains(77L);
    }
}
