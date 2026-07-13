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
import com.wms.inventory.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryIssueService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockChangedEventFactory stockChangedEventFactory;

    /**
     * Deducts ALLOCATED stock for shipment dispatch (warehouse exit / picking issue).
     */
    @Transactional
    public void issueStock(InventoryIssueRequest request) {
        TenantScopeGuard.assertMatchesContext(request.warehouseLocationId());
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();

        log.info("Issuing stock for shipment {} with {} product lines",
                request.shipmentNumber(), request.lines().size());

        for (InventoryIssueLine line : request.lines()) {
            issueProductLine(request, line, warehouseLocationId);
        }
    }

    private void issueProductLine(InventoryIssueRequest request, InventoryIssueLine line, Long warehouseLocationId) {
        List<Inventory> allocatedRows = inventoryRepository
                .findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
                        line.productCode(), InventoryStatus.ALLOCATED, warehouseLocationId);

        BigDecimal remaining = line.quantity();

        for (Inventory inventory : allocatedRows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal available = inventory.getQuantity();
            BigDecimal deduct = available.min(remaining);

            remaining = remaining.subtract(deduct);
            reduceOrDeleteInventory(inventory, deduct);

            transactionRepository.save(InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.PICKING)
                    .sourceLocationId(inventory.getStorageLocationId())
                    .companyId(inventory.getCompanyId())
                    .warehouseLocationId(inventory.getWarehouseLocationId())
                    .productCode(line.productCode())
                    .quantity(deduct)
                    .lotNumber(inventory.getLotNumber())
                    .serialNumber(inventory.getSerialNumber())
                    .performedByUserId(SYSTEM_USER_ID)
                    .build());

            stockChangedEventFactory.publish(
                    inventory.getCompanyId(),
                    inventory.getWarehouseLocationId(),
                    inventory.getStorageLocationId(),
                    line.productCode(),
                    inventory.getQuantity(),
                    deduct.negate(),
                    "ISSUE");
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                    "Insufficient allocated stock for product " + line.productCode()
                            + " in warehouse " + warehouseLocationId
                            + ". Remaining: " + remaining,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void reduceOrDeleteInventory(Inventory inventory, BigDecimal deduct) {
        BigDecimal newQty = inventory.getQuantity().subtract(deduct);
        if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
            inventoryRepository.delete(inventory);
        } else {
            inventory.setQuantity(newQty);
            inventoryRepository.save(inventory);
        }
    }
}
