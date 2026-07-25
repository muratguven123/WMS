package com.wms.inventory.service;

import com.wms.inventory.dto.InternalMoveRequest;
import com.wms.inventory.dto.InventoryAdjustRequest;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.entity.enums.InventoryTransactionType;
import com.wms.inventory.integration.CoreServiceClient;
import com.wms.inventory.messaging.DomainEventPublisher;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.security.TenantContext;
import com.wms.inventory.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class InventoryMoveServiceTest extends com.wms.inventory.InventoryPostgresTestBase {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private InventoryMoveService inventoryMoveService;

    @MockBean
    private CoreServiceClient coreServiceClient;

    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private Long sourceLocationId;
    private Long targetLocationId;
    private Long companyId;
    private Long warehouseId;

    @BeforeEach
    public void setup() {
        inventoryRepository.deleteAll();
        transactionRepository.deleteAll();

        sourceLocationId = 1L;
        targetLocationId = 2L;
        companyId = 1L;
        warehouseId = 1L;
        TenantContextHolder.setContext(new TenantContext(1L, companyId, warehouseId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void testMoveStockSuccess() {
        // Setup source stock
        Inventory sourceStock = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(sourceLocationId)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .lotNumber("LOT-A")
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.saveAndFlush(sourceStock);

        // Mock capacity check to return true
        Mockito.when(coreServiceClient.checkLocationCapacity(eq(targetLocationId), any(), any()))
                .thenReturn(true);

        // Move 4 units
        InternalMoveRequest request = new InternalMoveRequest(
                sourceLocationId, targetLocationId, "PROD-1", new BigDecimal("4.0000"), "LOT-A"
        );
        inventoryMoveService.moveStock(request);

        // Verify source stock is reduced to 6
        Inventory sourceLeft = inventoryRepository.findUniqueInventory(
                sourceLocationId, "PROD-1", "LOT-A", null, InventoryStatus.AVAILABLE
        ).orElseThrow();
        assertThat(sourceLeft.getQuantity()).isEqualByComparingTo("6.0000");

        // Verify target stock has 4 units
        Inventory targetAdded = inventoryRepository.findUniqueInventory(
                targetLocationId, "PROD-1", "LOT-A", null, InventoryStatus.AVAILABLE
        ).orElseThrow();
        assertThat(targetAdded.getQuantity()).isEqualByComparingTo("4.0000");

        // Verify capacity load update requests were sent
        Mockito.verify(coreServiceClient).updateLocationLoad(eq(sourceLocationId), any(), any(), eq(false));
        Mockito.verify(coreServiceClient).updateLocationLoad(eq(targetLocationId), any(), any(), eq(true));

        // Verify transaction logged
        List<InventoryTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionType()).isEqualTo(InventoryTransactionType.INTERNAL_MOVE);
        assertThat(transactions.get(0).getQuantity()).isEqualByComparingTo("4.0000");
    }

    @Test
    public void testAdjustStockPositiveVariance() {
        // Mock capacity check
        Mockito.when(coreServiceClient.checkLocationCapacity(eq(sourceLocationId), any(), any()))
                .thenReturn(true);

        // System stock is 0. Adjusting actual stock to 5 units
        InventoryAdjustRequest request = new InventoryAdjustRequest(
                sourceLocationId, "PROD-1", "LOT-A", null,
                new BigDecimal("5.0000"), companyId, warehouseId
        );
        inventoryMoveService.adjustStock(request);

        // Verify stock created with 5 units
        Inventory stock = inventoryRepository.findUniqueInventory(
                sourceLocationId, "PROD-1", "LOT-A", null, InventoryStatus.AVAILABLE
        ).orElseThrow();
        assertThat(stock.getQuantity()).isEqualByComparingTo("5.0000");

        // Verify capacity load addition request sent
        Mockito.verify(coreServiceClient).updateLocationLoad(eq(sourceLocationId), any(), any(), eq(true));

        // Verify transaction logged
        List<InventoryTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionType()).isEqualTo(InventoryTransactionType.ADJUSTMENT);
        assertThat(transactions.get(0).getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    public void testAdjustStockNegativeVariance() {
        // Setup initial stock of 10
        Inventory sourceStock = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(sourceLocationId)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .lotNumber("LOT-A")
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.saveAndFlush(sourceStock);

        // Adjust actual stock to 7 units (variance = -3)
        InventoryAdjustRequest request = new InventoryAdjustRequest(
                sourceLocationId, "PROD-1", "LOT-A", null,
                new BigDecimal("7.0000"), companyId, warehouseId
        );
        inventoryMoveService.adjustStock(request);

        // Verify stock reduced to 7
        Inventory stock = inventoryRepository.findUniqueInventory(
                sourceLocationId, "PROD-1", "LOT-A", null, InventoryStatus.AVAILABLE
        ).orElseThrow();
        assertThat(stock.getQuantity()).isEqualByComparingTo("7.0000");

        // Verify capacity load subtraction request sent
        Mockito.verify(coreServiceClient).updateLocationLoad(eq(sourceLocationId), any(), any(), eq(false));

        // Verify transaction logged (stores variance -3)
        List<InventoryTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionType()).isEqualTo(InventoryTransactionType.ADJUSTMENT);
        assertThat(transactions.get(0).getQuantity()).isEqualByComparingTo("-3.0000");
    }
}
