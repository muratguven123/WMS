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
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PutawayService {

    private final ReceiptRepository receiptRepository;
    private final PutawayEngineService putawayEngineService;

    @Transactional(readOnly = true)
    public List<PutawayRecommendationResponse> recommendForReceipt(Long receiptId) {
        if (receiptId == null) {
            throw new BusinessException(
                    "Receipt ID is required",
                    HttpStatus.BAD_REQUEST);
        }
        Receipt receipt = loadReceipt(receiptId);
        Long warehouseLocationId = resolveWarehouseLocationId(receipt);
        if (warehouseLocationId == null) {
            throw new BusinessException(
                    "Warehouse location ID is required",
                    HttpStatus.BAD_REQUEST);
        }
        return receipt.getItems().stream()
                .filter(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED)
                .map(item -> toRecommendation(item, warehouseLocationId))
                .toList();
    }

    @Transactional(readOnly = true)
    @Validated
    public Optional<PutawayRecommendationResponse> recommendForItem(Long receiptId, String productCode) {
        if (receiptId == null) {
            throw new BusinessException(
                    "Receipt ID is required",
                    HttpStatus.BAD_REQUEST);
        }
        if(productCode == null || productCode.isEmpty()) {
            throw new BusinessException(
                    "Product code is required",
                    HttpStatus.BAD_REQUEST);
        }
        return putawayEngineService.findRecommendedLocation(productCode, warehouseLocationId);
                .map(loc -> new PutawayRecommendationResponse(
                        productCode,
                        loc.id(),
                        loc.addressCode(),
                        loc.volumeUtilizationPercent()))
                .orElseGet(() -> new PutawayRecommendationResponse(
                        productCode, null, null, null));
    }

    @Validated
    private Receipt loadReceipt(Long receiptId) {
        if (receiptId == null) {
            throw new BusinessException(
                    "Receipt ID is required",
                    HttpStatus.BAD_REQUEST);
        }
        return receiptRepository.findWithDetailsById(receiptId)
                .orElseThrow(() -> new BusinessException(
                        "Receipt not found: " + receiptId, HttpStatus.NOT_FOUND));
    }

    @Validated
    private Long resolveWarehouseLocationId(Receipt receipt) {
        if (receipt == null) {
            throw new BusinessException(
                    "Receipt is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (receipt.getInboundOrder() == null) {
            throw new BusinessException(
                    "Inbound order is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (receipt.getInboundOrder().getWarehouseLocationId() == null) {
            throw new BusinessException(
                    "Inbound order has no warehouseLocationId configured for putaway",
                    HttpStatus.BAD_REQUEST);
        }
        return receipt.getInboundOrder().getWarehouseLocationId();
    }
}
