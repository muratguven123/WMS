package com.wms.inventory.service;

import com.wms.inventory.dto.InternalMoveRequest;
import com.wms.inventory.dto.InventoryAdjustRequest;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.enums.InventoryStatus;
import com.wms.inventory.entity.enums.InventoryTransactionType;
import com.wms.inventory.exception.BusinessException;
import com.wms.inventory.integration.CoreServiceClient;
import com.wms.inventory.messaging.StockChangedEventFactory;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMoveService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CoreServiceClient coreServiceClient;
    private final ProductDimensionResolver dimensionResolver;
    private final StockChangedEventFactory stockChangedEventFactory;

    private static final Long SYSTEM_USER_ID = 0L;

    /**
     * Moves stock from a source storage location to a target storage location.
     * Integrates with LocationCapacityService to check capacity and adjust loads.
     */
    @Transactional
    public void moveStock(InternalMoveRequest request) {
        log.info("Request to move stock: product={}, qty={}, source={}, target={}",
                request.productCode(), request.quantity(), request.sourceLocationId(), request.targetLocationId());

        if (request.sourceLocationId().equals(request.targetLocationId())) {
            throw new BusinessException("Source and target locations must be different", HttpStatus.BAD_REQUEST);
        }

        // 1. Fetch source inventory
        Inventory sourceInventory = inventoryRepository.findUniqueInventory(
                request.sourceLocationId(),
                request.productCode(),
                request.lotNumber(),
                null,
                InventoryStatus.AVAILABLE
        ).orElseThrow(() -> new BusinessException(
                String.format("Available stock not found at source location for product %s and lot %s",
                        request.productCode(), request.lotNumber()),
                HttpStatus.BAD_REQUEST
        ));

        TenantScopeGuard.assertEntityBelongsToContext(
                sourceInventory.getWarehouseLocationId(), sourceInventory.getCompanyId());

        // 2. Validate source quantity
        if (sourceInventory.getQuantity().compareTo(request.quantity()) < 0) {
            throw new BusinessException(
                    String.format("Insufficient stock at source location. Available: %s, Requested: %s",
                            sourceInventory.getQuantity(), request.quantity()),
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. Resolve item volume and weight
        BigDecimal unitVolume = dimensionResolver.getUnitVolume(request.productCode());
        BigDecimal unitWeight = dimensionResolver.getUnitWeight(request.productCode());
        BigDecimal volumeDelta = unitVolume.multiply(request.quantity());
        BigDecimal weightDelta = unitWeight.multiply(request.quantity());

        // 4. Validate capacity at target location
        boolean hasCapacity = coreServiceClient.checkLocationCapacity(request.targetLocationId(), volumeDelta, weightDelta);
        if (!hasCapacity) {
            throw new BusinessException("Target storage location does not have enough capacity", HttpStatus.BAD_REQUEST);
        }

        // 5. Update source location stock
        sourceInventory.setQuantity(sourceInventory.getQuantity().subtract(request.quantity()));
        if (sourceInventory.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }

        // 6. Update target location stock
        Optional<Inventory> targetOpt = inventoryRepository.findUniqueInventory(
                request.targetLocationId(),
                request.productCode(),
                request.lotNumber(),
                null,
                InventoryStatus.AVAILABLE
        );

        if (targetOpt.isPresent()) {
            Inventory targetInventory = targetOpt.get();
            targetInventory.setQuantity(targetInventory.getQuantity().add(request.quantity()));
            inventoryRepository.save(targetInventory);
        } else {
            Inventory newTargetInventory = Inventory.builder()
                    .productCode(request.productCode())
                    .storageLocationId(request.targetLocationId())
                    .quantity(request.quantity())
                    .lotNumber(request.lotNumber())
                    .serialNumber(null)
                    .status(InventoryStatus.AVAILABLE)
                    .expiryDate(sourceInventory.getExpiryDate())
                    .companyId(sourceInventory.getCompanyId())
                    .warehouseLocationId(sourceInventory.getWarehouseLocationId())
                    .updatedAt(LocalDateTime.now())
                    .build();
            inventoryRepository.save(newTargetInventory);
        }

        // 7. Synchronize location loads with LocationCapacityService
        coreServiceClient.updateLocationLoad(request.sourceLocationId(), volumeDelta, weightDelta, false);
        coreServiceClient.updateLocationLoad(request.targetLocationId(), volumeDelta, weightDelta, true);

        // 8. Log the INTERNAL_MOVE transaction
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.INTERNAL_MOVE)
                .sourceLocationId(request.sourceLocationId())
                .targetLocationId(request.targetLocationId())
                .companyId(sourceInventory.getCompanyId())
                .warehouseLocationId(sourceInventory.getWarehouseLocationId())
                .productCode(request.productCode())
                .quantity(request.quantity())
                .lotNumber(request.lotNumber())
                .serialNumber(null)
                .transactionDate(LocalDateTime.now())
                .performedByUserId(SYSTEM_USER_ID)
                .build();
        transactionRepository.save(tx);

        stockChangedEventFactory.publish(
                sourceInventory.getCompanyId(),
                sourceInventory.getWarehouseLocationId(),
                request.sourceLocationId(),
                request.productCode(),
                sourceInventory.getQuantity(),
                request.quantity().negate(),
                "INTERNAL_MOVE");
        stockChangedEventFactory.publish(
                sourceInventory.getCompanyId(),
                sourceInventory.getWarehouseLocationId(),
                request.targetLocationId(),
                request.productCode(),
                request.quantity(),
                request.quantity(),
                "INTERNAL_MOVE");

        log.info("Successfully moved {} units of product {} from {} to {}",
                request.quantity(), request.productCode(), request.sourceLocationId(), request.targetLocationId());
    }

    /**
     * Performs cycle counting adjustment.
     * Computes the difference (variance) and records it as an ADJUSTMENT transaction.
     */
    @Transactional
    public void adjustStock(InventoryAdjustRequest request) {
        TenantScopeGuard.assertMatchesContext(request.warehouseLocationId());
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        Long companyId = TenantScopeGuard.requireCompanyId();

        log.info("Request to adjust stock: product={}, actualQty={}, location={}",
                request.productCode(), request.actualQuantity(), request.storageLocationId());

        if (request.actualQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Actual quantity cannot be negative");
        }

        // 1. Fetch system stock
        Optional<Inventory> systemOpt = inventoryRepository.findUniqueInventory(
                request.storageLocationId(),
                request.productCode(),
                request.lotNumber(),
                request.serialNumber(),
                InventoryStatus.AVAILABLE
        );

        BigDecimal systemQuantity = systemOpt.isPresent() ? systemOpt.get().getQuantity() : BigDecimal.ZERO;
        if (systemOpt.isPresent()) {
            TenantScopeGuard.assertEntityBelongsToContext(
                    systemOpt.get().getWarehouseLocationId(), systemOpt.get().getCompanyId());
        }

        BigDecimal varianceQuantity = request.actualQuantity().subtract(systemQuantity);

        // 2. Process difference if there is variance
        if (varianceQuantity.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal unitVolume = dimensionResolver.getUnitVolume(request.productCode());
            BigDecimal unitWeight = dimensionResolver.getUnitWeight(request.productCode());
            BigDecimal absoluteVariance = varianceQuantity.abs();
            BigDecimal volumeDelta = unitVolume.multiply(absoluteVariance);
            BigDecimal weightDelta = unitWeight.multiply(absoluteVariance);

            if (varianceQuantity.compareTo(BigDecimal.ZERO) > 0) {
                // Positive variance (adding stock)
                boolean hasCapacity = coreServiceClient.checkLocationCapacity(request.storageLocationId(), volumeDelta, weightDelta);
                if (!hasCapacity) {
                    throw new BusinessException("Storage location does not have enough capacity for adjustment addition", HttpStatus.BAD_REQUEST);
                }

                if (systemOpt.isPresent()) {
                    Inventory inv = systemOpt.get();
                    inv.setQuantity(request.actualQuantity());
                    inventoryRepository.save(inv);
                } else {
                    Inventory newInv = Inventory.builder()
                            .productCode(request.productCode())
                            .storageLocationId(request.storageLocationId())
                            .quantity(request.actualQuantity())
                            .lotNumber(request.lotNumber())
                            .serialNumber(request.serialNumber())
                            .status(InventoryStatus.AVAILABLE)
                            .companyId(companyId)
                            .warehouseLocationId(warehouseLocationId)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    inventoryRepository.save(newInv);
                }

                // Add to capacity load
                coreServiceClient.updateLocationLoad(request.storageLocationId(), volumeDelta, weightDelta, true);

            } else {
                // Negative variance (reducing stock)
                Inventory inv = systemOpt.orElseThrow(() -> new BusinessException("System stock record not found", HttpStatus.INTERNAL_SERVER_ERROR));
                
                if (request.actualQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    inventoryRepository.delete(inv);
                } else {
                    inv.setQuantity(request.actualQuantity());
                    inventoryRepository.save(inv);
                }

                // Remove from capacity load
                coreServiceClient.updateLocationLoad(request.storageLocationId(), volumeDelta, weightDelta, false);
            }

            // 3. Log the ADJUSTMENT transaction
            InventoryTransaction tx = InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.ADJUSTMENT)
                    .sourceLocationId(request.storageLocationId())
                    .targetLocationId(null)
                    .companyId(companyId)
                    .warehouseLocationId(warehouseLocationId)
                    .productCode(request.productCode())
                    .quantity(varianceQuantity) // Stores variance (+/-)
                    .lotNumber(request.lotNumber())
                    .serialNumber(request.serialNumber())
                    .transactionDate(LocalDateTime.now())
                    .performedByUserId(SYSTEM_USER_ID)
                    .build();
            transactionRepository.save(tx);

            Long publishCompanyId = systemOpt.map(Inventory::getCompanyId).orElse(companyId);
            Long publishWarehouseId = systemOpt.map(Inventory::getWarehouseLocationId).orElse(warehouseLocationId);
            stockChangedEventFactory.publish(
                    publishCompanyId,
                    publishWarehouseId,
                    request.storageLocationId(),
                    request.productCode(),
                    request.actualQuantity(),
                    varianceQuantity,
                    "ADJUSTMENT");

            log.info("Adjusted product {} at {}. Variance: {}",
                    request.productCode(), request.storageLocationId(), varianceQuantity);
        } else {
            log.info("No variance detected for product {} at {}. System: {}, Actual: {}",
                    request.productCode(), request.storageLocationId(), systemQuantity, request.actualQuantity());
        }
    }
}
