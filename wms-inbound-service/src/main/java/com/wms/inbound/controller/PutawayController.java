package com.wms.inbound.controller;

import com.wms.inbound.dto.PutawayRecommendationResponse;
import com.wms.inbound.service.PutawayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inbound")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INBOUND_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class PutawayController {

    private final PutawayService putawayService;

    /**
     * QC'den geçmiş kalemler için akıllı raf yerleştirme önerilerini döner.
     */
    @GetMapping("/receipts/{receiptId}/putaway")
    public ResponseEntity<List<PutawayRecommendationResponse>> recommendPutaway(
            @PathVariable Long receiptId) {

        return ResponseEntity.ok(putawayService.recommendForReceipt(receiptId));
    }

    @GetMapping("/receipts/{receiptId}/items/{productCode}/putaway")
    public ResponseEntity<PutawayRecommendationResponse> recommendPutawayForItem(
            @PathVariable Long receiptId,
            @PathVariable String productCode) {

        return putawayService.recommendForItem(receiptId, productCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
