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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceiptInventoryService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CoreServiceClient coreServiceClient;
    private final ProductDimensionResolver dimensionResolver;
    private final StockChangedEventFactory stockChangedEventFactory;

    @Transactional
    public void applyReceiptApproved(ReceiptApprovedEvent event) {
        log.info("Applying approved receipt {} to inventory", event.receiptId());

        for (ReceiptItemEventDto item : event.items()) {
            if (item.recommendedStorageLocationId() == null) {
                log.warn("Skipping item {} — no recommended storage location in event", item.productCode());
                continue;
            }

            upsertAvailableStock(event, item);
            recordPutawayTransaction(event, item);
            updateLocationCapacity(item);
            stockChangedEventFactory.publish(
                    event.companyId(),
                    event.warehouseLocationId(),
                    item.recommendedStorageLocationId(),
                    item.productCode(),
                    item.quantity(),
                    item.quantity(),
                    "RECEIPT_PUTAWAY");
        }
    }

    private void upsertAvailableStock(ReceiptApprovedEvent event, ReceiptItemEventDto item) {
        String lotNumber = emptyToNull(item.lotNumber());
        String serialNumber = emptyToNull(item.serialNumber());

        inventoryRepository.findUniqueInventory(
                item.recommendedStorageLocationId(),
                item.productCode(),
                lotNumber,
                serialNumber,
                InventoryStatus.AVAILABLE
        ).ifPresentOrElse(existing -> {
            existing.setQuantity(existing.getQuantity().add(item.quantity()));
            inventoryRepository.save(existing);
        }, () -> {
            Inventory inventory = Inventory.builder()
                    .productCode(item.productCode())
                    .storageLocationId(item.recommendedStorageLocationId())
                    .quantity(item.quantity())
                    .lotNumber(lotNumber)
                    .serialNumber(serialNumber)
                    .status(InventoryStatus.AVAILABLE)
                    .companyId(event.companyId())
                    .warehouseLocationId(event.warehouseLocationId())
                    .updatedAt(LocalDateTime.now())
                    .build();
            inventoryRepository.save(inventory);
        });
    }

    private void recordPutawayTransaction(ReceiptApprovedEvent event, ReceiptItemEventDto item) {
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.PUTAWAY)
                .sourceLocationId(null)
                .targetLocationId(item.recommendedStorageLocationId())
                .companyId(event.companyId())
                .warehouseLocationId(event.warehouseLocationId())
                .productCode(item.productCode())
                .quantity(item.quantity())
                .lotNumber(emptyToNull(item.lotNumber()))
                .serialNumber(emptyToNull(item.serialNumber()))
                .transactionDate(LocalDateTime.now())
                .performedByUserId(SYSTEM_USER_ID)
                .build();
        transactionRepository.save(tx);
        log.info("PUTAWAY recorded for receipt {} product {} qty {}",
                event.receiptId(), item.productCode(), item.quantity());
    }

    private void updateLocationCapacity(ReceiptItemEventDto item) {
        BigDecimal volumeDelta = dimensionResolver.getUnitVolume(item.productCode()).multiply(item.quantity());
        BigDecimal weightDelta = dimensionResolver.getUnitWeight(item.productCode()).multiply(item.quantity());
        coreServiceClient.updateLocationLoad(item.recommendedStorageLocationId(), volumeDelta, weightDelta, true);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
