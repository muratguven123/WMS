package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.entity.enums.InventoryTransactionType;
import com.wms.inventory.integration.CoreServiceClient;
import com.wms.inventory.messaging.ReceiptApprovedEvent;
import com.wms.inventory.messaging.ReceiptItemEventDto;
import com.wms.inventory.messaging.StockChangedEventFactory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundReceiptInventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private ProductDimensionResolver dimensionResolver;

    @Mock
    private StockChangedEventFactory stockChangedEventFactory;

    @InjectMocks
    private InboundReceiptInventoryService inboundReceiptInventoryService;

    private Long companyId;
    private Long warehouseId;
    private Long storageLocationId;
    private Long receiptId;

    @BeforeEach
    void setUp() {
        companyId = 1L;
        warehouseId = 1L;
        storageLocationId = 1L;
        receiptId = 1L;
    }

    @Test
    void applyReceiptApproved_createsInventoryAndPutawayTransaction() {
        ReceiptApprovedEvent event = new ReceiptApprovedEvent(
                receiptId,
                1L,
                companyId,
                warehouseId,
                Instant.now(),
                List.of(new ReceiptItemEventDto(
                        "PROD-1",
                        new BigDecimal("5.0000"),
                        "LOT-A",
                        "",
                        storageLocationId))
        );

        when(inventoryRepository.findUniqueInventory(
                eq(storageLocationId), eq("PROD-1"), eq("LOT-A"), eq(null), eq(InventoryStatus.AVAILABLE)))
                .thenReturn(Optional.empty());
        when(dimensionResolver.getUnitVolume("PROD-1")).thenReturn(new BigDecimal("0.0100"));
        when(dimensionResolver.getUnitWeight("PROD-1")).thenReturn(new BigDecimal("1.0000"));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        inboundReceiptInventoryService.applyReceiptApproved(event);

        verify(inventoryRepository).save(any(Inventory.class));
        verify(transactionRepository).save(any(InventoryTransaction.class));
        verify(coreServiceClient).updateLocationLoad(eq(storageLocationId), any(), any(), eq(true));
    }

    @Test
    void applyReceiptApproved_incrementsExistingStock() {
        Inventory existing = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(storageLocationId)
                .quantity(new BigDecimal("3.0000"))
                .lotNumber("LOT-A")
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .build();

        ReceiptApprovedEvent event = new ReceiptApprovedEvent(
                receiptId, 1L, companyId, warehouseId, Instant.now(),
                List.of(new ReceiptItemEventDto("PROD-1", new BigDecimal("2.0000"), "LOT-A", "", storageLocationId))
        );

        when(inventoryRepository.findUniqueInventory(
                eq(storageLocationId), eq("PROD-1"), eq("LOT-A"), eq(null), eq(InventoryStatus.AVAILABLE)))
                .thenReturn(Optional.of(existing));
        when(dimensionResolver.getUnitVolume("PROD-1")).thenReturn(BigDecimal.ONE);
        when(dimensionResolver.getUnitWeight("PROD-1")).thenReturn(BigDecimal.ONE);

        inboundReceiptInventoryService.applyReceiptApproved(event);

        assertThat(existing.getQuantity()).isEqualByComparingTo("5.0000");
        verify(inventoryRepository).save(existing);
    }
}
