package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.ReceiptApprovalDto;
import com.wms.integration.outbox.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * wms-inbound-service'ten gelen mal kabul onaylarını entegrasyon outbox'ına yazar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpReceiptIntegrationService {

    private final OutboxPublisherService outboxPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueReceipt(String rawJsonPayload) {
        try {
            ReceiptApprovalDto receipt = objectMapper.readValue(rawJsonPayload, ReceiptApprovalDto.class);

            Long locationId = receipt.getWarehouseLocationId() != null
                    ? receipt.getWarehouseLocationId()
                    : receipt.getCompanyId();

            if (locationId == null) {
                throw new IllegalArgumentException("warehouseLocationId or companyId is required");
            }

            outboxPublisher.saveOutboxMessage(
                    "Receipt",
                    receipt.getReceiptId(),
                    "RECEIPT_SYNC",
                    locationId,
                    receipt);

            log.info("Receipt approval queued for ERP sync: receiptNumber={}, locationId={}",
                    receipt.getReceiptNumber(), locationId);

        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid receipt approval payload: " + ex.getMessage(), ex);
        }
    }
}
