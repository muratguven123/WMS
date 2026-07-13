package com.wms.inventory.service;

import com.wms.inventory.dto.InventoryIssueLine;
import com.wms.inventory.dto.InventoryIssueRequest;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.entity.enums.InventoryTransactionType;
import com.wms.inventory.exception.BusinessException;
import com.wms.inventory.messaging.StockChangedEventFactory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.security.TenantContext;
import com.wms.inventory.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryIssueServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private StockChangedEventFactory stockChangedEventFactory;

    @InjectMocks
    private InventoryIssueService inventoryIssueService;

    private Long warehouseLocationId;
    private Long companyId;
    private Inventory allocatedInventory;

    @BeforeEach
    void setUp() {
        warehouseLocationId = 1L;
        companyId = 1L;

        allocatedInventory = Inventory.builder()
                .id(1L)
                .productCode("SKU-001")
                .storageLocationId(1L)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.ALLOCATED)
                .companyId(companyId)
                .warehouseLocationId(warehouseLocationId)
                .build();
        TenantContextHolder.setContext(new TenantContext(1L, companyId, warehouseLocationId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void issueStock_shouldDeductAllocatedInventory_andRecordPickingTransaction() {
        when(inventoryRepository.findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                "SKU-001", InventoryStatus.ALLOCATED, warehouseLocationId))
                .thenReturn(List.of(allocatedInventory));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InventoryIssueRequest request = new InventoryIssueRequest(
                1L,
                "SH-100",
                companyId,
                warehouseLocationId,
                List.of(new InventoryIssueLine("SKU-001", new BigDecimal("5.0000"))));

        inventoryIssueService.issueStock(request);

        assertThat(allocatedInventory.getQuantity()).isEqualByComparingTo("5.0000");
        verify(inventoryRepository).save(allocatedInventory);
        verify(inventoryRepository, never()).delete(any());

        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(InventoryTransactionType.PICKING);
        assertThat(txCaptor.getValue().getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    void issueStock_shouldDeleteInventoryRow_whenFullyIssued() {
        when(inventoryRepository.findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                "SKU-001", InventoryStatus.ALLOCATED, warehouseLocationId))
                .thenReturn(List.of(allocatedInventory));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InventoryIssueRequest request = new InventoryIssueRequest(
                1L,
                "SH-100",
                companyId,
                warehouseLocationId,
                List.of(new InventoryIssueLine("SKU-001", new BigDecimal("10.0000"))));

        inventoryIssueService.issueStock(request);

        verify(inventoryRepository).delete(allocatedInventory);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void issueStock_shouldThrow_whenInsufficientAllocatedStock() {
        when(inventoryRepository.findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                "SKU-001", InventoryStatus.ALLOCATED, warehouseLocationId))
                .thenReturn(List.of(allocatedInventory));

        InventoryIssueRequest request = new InventoryIssueRequest(
                1L,
                "SH-100",
                companyId,
                warehouseLocationId,
                List.of(new InventoryIssueLine("SKU-001", new BigDecimal("15.0000"))));

        assertThatThrownBy(() -> inventoryIssueService.issueStock(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient allocated stock");
    }
}
