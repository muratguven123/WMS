package com.wms.inventory.repository;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.enums.InventoryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class InventoryRepositoryTest extends com.wms.inventory.InventoryPostgresTestBase {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    public void testSaveAndFindUniqueInventory() {
        Long storageLocationId = 1L;
        Long companyId = 1L;
        Long warehouseId = 1L;

        Inventory inventory = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(new BigDecimal("10.0000"))
                .lotNumber("LOT-A")
                .serialNumber(null)
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        inventoryRepository.save(inventory);

        Optional<Inventory> found = inventoryRepository.findUniqueInventory(
                storageLocationId, "PROD-001", "LOT-A", null, InventoryStatus.AVAILABLE
        );

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    public void testFifoSorting() throws InterruptedException {
        Long storageLocationId = 1L;
        Long companyId = 1L;
        Long warehouseId = 1L;

        // inv1 represents the older stock (FIFO candidate)
        Inventory inv1 = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(BigDecimal.ONE)
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        // inv2 represents the newer stock
        Inventory inv2 = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(BigDecimal.TEN)
                .status(InventoryStatus.AVAILABLE)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        inventoryRepository.saveAndFlush(inv1);
        Thread.sleep(50); // Delay to ensure distinct updatedAt timestamp
        inventoryRepository.saveAndFlush(inv2);

        List<Inventory> list = inventoryRepository.findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                "PROD-001", InventoryStatus.AVAILABLE, warehouseId
        );

        assertThat(list).hasSize(2);
        // inv1 (quantity = 1) was saved first and is the oldest
        assertThat(list.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    public void testFefoSorting() {
        Long storageLocationId = 1L;
        Long companyId = 1L;
        Long warehouseId = 1L;

        Inventory inv1 = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(BigDecimal.TEN)
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(LocalDate.now().plusDays(10))
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        Inventory inv2 = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(BigDecimal.ONE)
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(LocalDate.now().plusDays(5))
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        Inventory inv3 = Inventory.builder()
                .productCode("PROD-001")
                .storageLocationId(storageLocationId)
                .quantity(BigDecimal.TEN)
                .status(InventoryStatus.AVAILABLE)
                .expiryDate(null)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .updatedAt(LocalDateTime.now())
                .build();

        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
        inventoryRepository.save(inv3);

        List<Inventory> list = inventoryRepository.findAvailableStockForFefo(
                "PROD-001", InventoryStatus.AVAILABLE, warehouseId
        );

        assertThat(list).hasSize(3);
        // inv2 (5 days) -> inv1 (10 days) -> inv3 (null expiryDate)
        assertThat(list.get(0).getExpiryDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(list.get(1).getExpiryDate()).isEqualTo(LocalDate.now().plusDays(10));
        assertThat(list.get(2).getExpiryDate()).isNull();
    }
}
