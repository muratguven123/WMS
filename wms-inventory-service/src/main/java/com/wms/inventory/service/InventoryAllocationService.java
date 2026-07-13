package com.wms.inventory.service;

import com.wms.inventory.dto.AllocatedStockDto;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.exception.BusinessException;
import com.wms.inventory.messaging.StockChangedEventFactory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;
    private final StockChangedEventFactory stockChangedEventFactory;

    /**
     * Allocates stock for shipping based on the specified strategy (FIFO or FEFO).
     * Updates allocated quantities to ALLOCATED status.
     *
     * @param productCode          Product SKU code to allocate
     * @param requiredQuantity     Quantity needed
     * @param allocationStrategy   "FIFO" or "FEFO"
     * @param warehouseLocationId  Warehouse ID
     * @return List of AllocatedStockDto detailing allocated locations, lots and quantities
     */
    @Transactional
    public List<AllocatedStockDto> allocateStock(
            String productCode,
            BigDecimal requiredQuantity,
            String allocationStrategy,
            Long warehouseLocationId) {

        TenantScopeGuard.assertMatchesContext(warehouseLocationId);
        warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();

        if (requiredQuantity == null || requiredQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Required quantity must be greater than zero");
        }

        log.info("Request to allocate {} of product {} in warehouse {} using strategy {}",
                requiredQuantity, productCode, warehouseLocationId, allocationStrategy);

        // 1. Fetch available stock according to the strategy
        List<Inventory> availableInventories;
        if ("FEFO".equalsIgnoreCase(allocationStrategy)) {
            availableInventories = inventoryRepository.findAvailableStockForFefo(
                    productCode, InventoryStatus.AVAILABLE, warehouseLocationId);
        } else {
            // Default to FIFO
            availableInventories = inventoryRepository.findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                    productCode, InventoryStatus.AVAILABLE, warehouseLocationId);
        }

        List<AllocatedStockDto> allocatedStocks = new ArrayList<>();
        BigDecimal remainingToAllocate = requiredQuantity;

        for (Inventory inventory : availableInventories) {
            if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal currentQty = inventory.getQuantity();
            BigDecimal allocatedQty;

            if (currentQty.compareTo(remainingToAllocate) <= 0) {
                // Allocate the entire available row
                allocatedQty = currentQty;
                remainingToAllocate = remainingToAllocate.subtract(allocatedQty);

                // Merge this entire allocated stock into an existing ALLOCATED row if present
                Optional<Inventory> existingAllocated = inventoryRepository.findUniqueInventory(
                        inventory.getStorageLocationId(),
                        inventory.getProductCode(),
                        inventory.getLotNumber(),
                        inventory.getSerialNumber(),
                        InventoryStatus.ALLOCATED
                );

                if (existingAllocated.isPresent()) {
                    Inventory allocatedRow = existingAllocated.get();
                    allocatedRow.setQuantity(allocatedRow.getQuantity().add(allocatedQty));
                    inventoryRepository.save(allocatedRow);
                    inventoryRepository.delete(inventory); // delete the now empty AVAILABLE row
                } else {
                    // Turn this AVAILABLE row directly into an ALLOCATED row
                    inventory.setStatus(InventoryStatus.ALLOCATED);
                    inventoryRepository.save(inventory);
                }
            } else {
                // Partial allocation: split the available row
                allocatedQty = remainingToAllocate;
                remainingToAllocate = BigDecimal.ZERO;

                // Reduce the AVAILABLE row's quantity
                inventory.setQuantity(currentQty.subtract(allocatedQty));
                inventoryRepository.save(inventory);

                // Create or update the ALLOCATED row
                Optional<Inventory> existingAllocated = inventoryRepository.findUniqueInventory(
                        inventory.getStorageLocationId(),
                        inventory.getProductCode(),
                        inventory.getLotNumber(),
                        inventory.getSerialNumber(),
                        InventoryStatus.ALLOCATED
                );

                if (existingAllocated.isPresent()) {
                    Inventory allocatedRow = existingAllocated.get();
                    allocatedRow.setQuantity(allocatedRow.getQuantity().add(allocatedQty));
                    inventoryRepository.save(allocatedRow);
                } else {
                    Inventory newAllocated = Inventory.builder()
                            .productCode(inventory.getProductCode())
                            .storageLocationId(inventory.getStorageLocationId())
                            .quantity(allocatedQty)
                            .lotNumber(inventory.getLotNumber())
                            .serialNumber(inventory.getSerialNumber())
                            .status(InventoryStatus.ALLOCATED)
                            .expiryDate(inventory.getExpiryDate())
                            .companyId(inventory.getCompanyId())
                            .warehouseLocationId(inventory.getWarehouseLocationId())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    inventoryRepository.save(newAllocated);
                }
            }

            allocatedStocks.add(new AllocatedStockDto(
                    inventory.getStorageLocationId(),
                    inventory.getLotNumber(),
                    allocatedQty
            ));

            stockChangedEventFactory.publish(
                    inventory.getCompanyId(),
                    inventory.getWarehouseLocationId(),
                    inventory.getStorageLocationId(),
                    productCode,
                    allocatedQty,
                    allocatedQty.negate(),
                    "ALLOCATE");
        }

        // 2. If we couldn't fulfill the entire request, throw a BusinessException (rollback transaction)
        if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Insufficient stock for product {}. Shortage: {}", productCode, remainingToAllocate);
            throw new BusinessException(
                    String.format("Insufficient stock for product %s. Needed: %s, Shortage: %s",
                            productCode, requiredQuantity, remainingToAllocate),
                    HttpStatus.BAD_REQUEST
            );
        }

        log.info("Successfully allocated {} units of product {}", requiredQuantity, productCode);
        return allocatedStocks;
    }
}
