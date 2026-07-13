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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PutawayEngineServiceTest {

    @Mock
    private CoreServiceClient coreServiceClient;

    private ZoneMatchStrategy zoneMatchStrategy;
    private CapacityMatchStrategy capacityMatchStrategy;
    private PutawayEngineService putawayEngineService;

    private Long warehouseId;
    private Long otherWarehouseId;
    private Long standardZoneId;
    private Long coldZoneId;

    private StorageLocationResponse locStandardBest;
    private StorageLocationResponse locStandardHighUtil;
    private StorageLocationResponse locStandardSmall;
    private StorageLocationResponse locCold;
    private StorageLocationResponse locOtherWarehouse;

    @BeforeEach
    void setUp() {
        zoneMatchStrategy = new ZoneMatchStrategy();
        capacityMatchStrategy = new CapacityMatchStrategy();
        putawayEngineService = new PutawayEngineService(coreServiceClient, zoneMatchStrategy, capacityMatchStrategy);

        warehouseId = 1L;
        otherWarehouseId = 1L;
        standardZoneId = 1L;
        coldZoneId = 1L;

        // 1. Standard location with 10% volume utilization (Best choice for standard product)
        locStandardBest = new StorageLocationResponse(
                1L, standardZoneId, warehouseId, "STD_ZONE", "STANDARD",
                "A-01-01-01", "A", "01", "01", "01",
                new BigDecimal("10.0000"), new BigDecimal("100.0000"),
                new BigDecimal("1.0000"), new BigDecimal("10.0000"),
                new BigDecimal("10.00"), StorageLocationStatus.ACTIVE, true
        );

        // 2. Standard location with 80% volume utilization
        locStandardHighUtil = new StorageLocationResponse(
                1L, standardZoneId, warehouseId, "STD_ZONE", "STANDARD",
                "A-01-01-02", "A", "01", "01", "02",
                new BigDecimal("10.0000"), new BigDecimal("100.0000"),
                new BigDecimal("8.0000"), new BigDecimal("80.0000"),
                new BigDecimal("80.00"), StorageLocationStatus.ACTIVE, true
        );

        // 3. Standard location with insufficient remaining weight capacity
        locStandardSmall = new StorageLocationResponse(
                1L, standardZoneId, warehouseId, "STD_ZONE", "STANDARD",
                "A-01-01-03", "A", "01", "01", "03",
                new BigDecimal("10.0000"), new BigDecimal("10.0000"), // Max weight is only 10kg
                new BigDecimal("1.0000"), new BigDecimal("9.0000"), // Remaining weight is 1kg
                new BigDecimal("10.00"), StorageLocationStatus.ACTIVE, true
        );

        // 4. Cold room location
        locCold = new StorageLocationResponse(
                1L, coldZoneId, warehouseId, "COLD_ZONE", "COLD_ROOM",
                "C-01-01-01", "C", "01", "01", "01",
                new BigDecimal("20.0000"), new BigDecimal("200.0000"),
                new BigDecimal("2.0000"), new BigDecimal("20.0000"),
                new BigDecimal("10.00"), StorageLocationStatus.ACTIVE, true
        );

        // 5. Location belonging to a different warehouse
        locOtherWarehouse = new StorageLocationResponse(
                1L, 1L, otherWarehouseId, "STD_ZONE", "STANDARD",
                "B-01-01-01", "B", "01", "01", "01",
                new BigDecimal("50.0000"), new BigDecimal("500.0000"),
                new BigDecimal("0.0000"), new BigDecimal("0.0000"),
                BigDecimal.ZERO, StorageLocationStatus.ACTIVE, true
        );
    }

    @Test
    void findPutawayLocation_standardProduct_returnsStandardLocationWithLowestUtilization() {
        // Arrange
        InboundOrder order = new InboundOrder();
        InboundOrderItem orderItem = InboundOrderItem.builder()
                .productCode("STD_ITEM_01")
                .unitVolume(new BigDecimal("0.5000"))  // Total: 0.5000 * 2 = 1.0000 m3
                .unitWeight(new BigDecimal("5.0000"))  // Total: 5.0000 * 2 = 10.0000 kg
                .build();
        order.setItems(List.of(orderItem));

        Receipt receipt = Receipt.builder().inboundOrder(order).build();
        ReceiptItem receiptItem = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("STD_ITEM_01")
                .quantity(new BigDecimal("2.0000"))
                .build();

        List<StorageLocationResponse> coreLocations = Arrays.asList(
                locStandardBest, locStandardHighUtil, locStandardSmall, locCold, locOtherWarehouse
        );
        when(coreServiceClient.getActiveLocations()).thenReturn(coreLocations);

        // Act
        Optional<Long> recommendedLocationId = putawayEngineService.findPutawayLocation(receiptItem, warehouseId);

        // Assert
        assertThat(recommendedLocationId).isPresent();
        assertThat(recommendedLocationId.get()).isEqualTo(locStandardBest.id());
    }

    @Test
    void findPutawayLocation_coldProduct_returnsColdRoomLocation() {
        // Arrange
        InboundOrder order = new InboundOrder();
        InboundOrderItem orderItem = InboundOrderItem.builder()
                .productCode("COLD_MILK")
                .unitVolume(new BigDecimal("0.1000"))
                .unitWeight(new BigDecimal("1.0000"))
                .build();
        order.setItems(List.of(orderItem));

        Receipt receipt = Receipt.builder().inboundOrder(order).build();
        ReceiptItem receiptItem = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("COLD_MILK")
                .quantity(new BigDecimal("10.0000"))
                .build();

        List<StorageLocationResponse> coreLocations = Arrays.asList(
                locStandardBest, locCold, locOtherWarehouse
        );
        when(coreServiceClient.getActiveLocations()).thenReturn(coreLocations);

        // Act
        Optional<Long> recommendedLocationId = putawayEngineService.findPutawayLocation(receiptItem, warehouseId);

        // Assert
        assertThat(recommendedLocationId).isPresent();
        assertThat(recommendedLocationId.get()).isEqualTo(locCold.id());
    }

    @Test
    void findPutawayLocation_coldProductNoColdZone_fallsBackToStandardZone() {
        // Arrange
        InboundOrder order = new InboundOrder();
        InboundOrderItem orderItem = InboundOrderItem.builder()
                .productCode("COLD_MILK")
                .unitVolume(new BigDecimal("0.1000"))
                .unitWeight(new BigDecimal("1.0000"))
                .build();
        order.setItems(List.of(orderItem));

        Receipt receipt = Receipt.builder().inboundOrder(order).build();
        ReceiptItem receiptItem = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("COLD_MILK")
                .quantity(new BigDecimal("10.0000"))
                .build();

        // No cold zone available in coreLocations
        List<StorageLocationResponse> coreLocations = Arrays.asList(
                locStandardBest, locOtherWarehouse
        );
        when(coreServiceClient.getActiveLocations()).thenReturn(coreLocations);

        // Act
        Optional<Long> recommendedLocationId = putawayEngineService.findPutawayLocation(receiptItem, warehouseId);

        // Assert
        assertThat(recommendedLocationId).isPresent();
        assertThat(recommendedLocationId.get()).isEqualTo(locStandardBest.id());
    }

    @Test
    void findPutawayLocation_insufficientCapacity_returnsEmpty() {
        // Arrange
        InboundOrder order = new InboundOrder();
        InboundOrderItem orderItem = InboundOrderItem.builder()
                .productCode("HEAVY_ITEM")
                .unitVolume(new BigDecimal("5.0000"))
                .unitWeight(new BigDecimal("200.0000")) // Exceeds standard weight limits
                .build();
        order.setItems(List.of(orderItem));

        Receipt receipt = Receipt.builder().inboundOrder(order).build();
        ReceiptItem receiptItem = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("HEAVY_ITEM")
                .quantity(new BigDecimal("1.0000"))
                .build();

        List<StorageLocationResponse> coreLocations = Arrays.asList(
                locStandardBest, locStandardHighUtil, locStandardSmall
        );
        when(coreServiceClient.getActiveLocations()).thenReturn(coreLocations);

        // Act
        Optional<Long> recommendedLocationId = putawayEngineService.findPutawayLocation(receiptItem, warehouseId);

        // Assert
        assertThat(recommendedLocationId).isEmpty();
    }

    @Test
    void findPutawayLocation_emptyLocationsList_returnsEmpty() {
        // Arrange
        ReceiptItem receiptItem = ReceiptItem.builder()
                .productCode("STD_ITEM_01")
                .quantity(BigDecimal.ONE)
                .build();

        when(coreServiceClient.getActiveLocations()).thenReturn(Collections.emptyList());

        // Act
        Optional<Long> recommendedLocationId = putawayEngineService.findPutawayLocation(receiptItem, warehouseId);

        // Assert
        assertThat(recommendedLocationId).isEmpty();
    }
}
