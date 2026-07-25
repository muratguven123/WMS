package com.wms.inventory.service;

import com.wms.inventory.dto.AllocatedStockDto;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.exception.BusinessException;
import com.wms.inventory.messaging.DomainEventPublisher;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.security.TenantContext;
import com.wms.inventory.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class InventoryAllocationServiceTest extends com.wms.inventory.InventoryPostgresTestBase {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryAllocationService inventoryAllocationService;

    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private Long warehouseId;
    private Long companyId;

    @BeforeEach
    public void setup() {
        inventoryRepository.deleteAll();
        warehouseId = 1L;
        companyId = 1L;
        TenantContextHolder.setContext(new TenantContext(1L, companyId, warehouseId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void testFifoAllocation() {
        Long loc1 = 1L;
        Long loc2 = 2L;
        LocalDateTime olderTimestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime newerTimestamp = LocalDateTime.of(2026, 1, 2, 10, 0);

        Inventory inv1 = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc1)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(olderTimestamp)
                .build();
        inventoryRepository.saveAndFlush(inv1);

        Inventory inv2 = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc2)
                .quantity(new BigDecimal("20.0000"))
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(newerTimestamp)
                .build();
        inventoryRepository.saveAndFlush(inv2);

        // Allocate 15 units
        List<AllocatedStockDto> allocations = inventoryAllocationService.allocateStock(
                "PROD-1", new BigDecimal("15.0000"), "FIFO", warehouseId
        );

        // Should allocate 10 units from loc1 (entire row) and 5 units from loc2 (partial row)
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).storageLocationId()).isEqualTo(loc1);
        assertThat(allocations.get(0).allocatedQuantity()).isEqualByComparingTo("10.0000");

        assertThat(allocations.get(1).storageLocationId()).isEqualTo(loc2);
        assertThat(allocations.get(1).allocatedQuantity()).isEqualByComparingTo("5.0000");

        // Verify database quantities
        // loc1 should be ALLOCATED with 10 units (original AVAILABLE row deleted or changed to ALLOCATED)
        List<Inventory> loc1Stock = inventoryRepository.findAll().stream()
                .filter(i -> i.getStorageLocationId().equals(loc1))
                .toList();
        assertThat(loc1Stock).hasSize(1);
        assertThat(loc1Stock.get(0).getStatus()).isEqualTo(InventoryStatus.ALLOCATED);
        assertThat(loc1Stock.get(0).getQuantity()).isEqualByComparingTo("10.0000");

        // loc2 should have 15 AVAILABLE remaining and 5 ALLOCATED
        List<Inventory> loc2Stock = inventoryRepository.findAll().stream()
                .filter(i -> i.getStorageLocationId().equals(loc2))
                .toList();
        assertThat(loc2Stock).hasSize(2);
        
        Inventory loc2Available = loc2Stock.stream()
                .filter(i -> i.getStatus() == InventoryStatus.AVAILABLE)
                .findFirst().orElseThrow();
        assertThat(loc2Available.getQuantity()).isEqualByComparingTo("15.0000");

        Inventory loc2Allocated = loc2Stock.stream()
                .filter(i -> i.getStatus() == InventoryStatus.ALLOCATED)
                .findFirst().orElseThrow();
        assertThat(loc2Allocated.getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    public void testFefoAllocation() {
        Long loc1 = 1L;
        Long loc2 = 2L;
        Long loc3 = 1L;

        // Expiring in 10 days
        Inventory inv1 = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc1)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(LocalDate.now().plusDays(10))
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        // Expiring in 5 days (earliest)
        Inventory inv2 = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc2)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(LocalDate.now().plusDays(5))
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        // Expiring never (null expiryDate, should be last)
        Inventory inv3 = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc3)
                .quantity(new BigDecimal("10.0000"))
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(null)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
        inventoryRepository.save(inv3);

        // Allocate 15 units with FEFO
        List<AllocatedStockDto> allocations = inventoryAllocationService.allocateStock(
                "PROD-1", new BigDecimal("15.0000"), "FEFO", warehouseId
        );

        // Should take 10 units from loc2 (earliest expiry) and 5 units from loc1 (second earliest)
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).storageLocationId()).isEqualTo(loc2);
        assertThat(allocations.get(0).allocatedQuantity()).isEqualByComparingTo("10.0000");

        assertThat(allocations.get(1).storageLocationId()).isEqualTo(loc1);
        assertThat(allocations.get(1).allocatedQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    public void testInsufficientStockThrowsException() {
        Long loc1 = 1L;

        Inventory inv = Inventory.builder()
                .productCode("PROD-1")
                .storageLocationId(loc1)
                .quantity(new BigDecimal("5.0000"))
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();
        inventoryRepository.save(inv);

        assertThatThrownBy(() -> 
            inventoryAllocationService.allocateStock("PROD-1", new BigDecimal("10.0000"), "FIFO", warehouseId)
        ).isInstanceOf(BusinessException.class)
         .hasMessageContaining("Insufficient stock")
         .extracting(e -> ((BusinessException) e).getStatus())
         .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
