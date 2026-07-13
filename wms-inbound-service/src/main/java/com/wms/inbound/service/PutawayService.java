package com.wms.inbound.service;

import com.wms.inbound.dto.PutawayRecommendationResponse;
import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import com.wms.inbound.exception.BusinessException;
import com.wms.inbound.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PutawayService {

    private final ReceiptRepository receiptRepository;
    private final PutawayEngineService putawayEngineService;

    @Transactional(readOnly = true)
    public List<PutawayRecommendationResponse> recommendForReceipt(Long receiptId) {
        Receipt receipt = loadReceipt(receiptId);
        Long warehouseLocationId = resolveWarehouseLocationId(receipt);

        return receipt.getItems().stream()
                .filter(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED)
                .map(item -> toRecommendation(item, warehouseLocationId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PutawayRecommendationResponse> recommendForItem(Long receiptId, String productCode) {
        Receipt receipt = loadReceipt(receiptId);
        Long warehouseLocationId = resolveWarehouseLocationId(receipt);

        return receipt.getItems().stream()
                .filter(item -> item.getProductCode().equals(productCode))
                .filter(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED)
                .findFirst()
                .map(item -> toRecommendation(item, warehouseLocationId));
    }

    private PutawayRecommendationResponse toRecommendation(ReceiptItem item, Long warehouseLocationId) {
        Optional<StorageLocationResponse> location =
                putawayEngineService.findRecommendedLocation(item, warehouseLocationId);

        return location
                .map(loc -> new PutawayRecommendationResponse(
                        item.getProductCode(),
                        loc.id(),
                        loc.addressCode(),
                        loc.volumeUtilizationPercent()))
                .orElseGet(() -> new PutawayRecommendationResponse(
                        item.getProductCode(), null, null, null));
    }

    private Receipt loadReceipt(Long receiptId) {
        return receiptRepository.findWithDetailsById(receiptId)
                .orElseThrow(() -> new BusinessException(
                        "Receipt not found: " + receiptId, HttpStatus.NOT_FOUND));
    }

    private Long resolveWarehouseLocationId(Receipt receipt) {
        Long warehouseLocationId = receipt.getInboundOrder().getWarehouseLocationId();
        if (warehouseLocationId == null) {
            throw new BusinessException(
                    "Inbound order has no warehouseLocationId configured for putaway",
                    HttpStatus.BAD_REQUEST);
        }
        return warehouseLocationId;
    }
}
