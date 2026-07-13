package com.wms.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.ErpAdapterFactory;
import com.wms.integration.adapter.dto.ErpResponse;
import com.wms.integration.adapter.dto.InvoiceDto;
import com.wms.integration.adapter.dto.MaterialDto;
import com.wms.integration.adapter.dto.MovementDto;
import com.wms.integration.adapter.dto.ReceiptApprovalDto;
import com.wms.integration.adapter.dto.ShipmentDispatchDto;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.OutboxMessageRepository;
import com.wms.integration.service.IntegrationLogSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Tek bir Outbox mesajını kendi transaction'ında işler.
 * Batch içinde bir mesajın hatası diğerlerinin commit'ini engellemez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageProcessor {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ErpAdapterFactory erpAdapterFactory;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxAlertService alertService;
    private final IntegrationLogSyncService logSyncService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long messageId) {
        OutboxMessage message = outboxMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException(
                        "OutboxMessage not found: id=" + messageId));

        log.debug("[OutboxProcessor] Processing: id={}, job={}, attempt={}",
                message.getId(), message.getJobCode(), message.getRetryCount() + 1);

        message.setStatus(OutboxStatus.PROCESSING);
        message.setLastAttemptAt(OffsetDateTime.now());
        outboxMessageRepository.save(message);

        try {
            ErpAdapter adapter = erpAdapterFactory.getAdapter(message.getLocationId());
            ErpResponse response = dispatch(adapter, message);

            if (response.isSuccess()) {
                handleSuccess(message, response);
            } else {
                handleFailure(message, response.getMessage());
            }

        } catch (ErpAdapterFactory.ErpAdapterNotFoundException ex) {
            log.error("[OutboxProcessor] No adapter for locationId={}: {}",
                    message.getLocationId(), ex.getMessage());
            markPermanentFailure(message, ex.getMessage());

        } catch (Exception ex) {
            log.error("[OutboxProcessor] Unexpected error for messageId={}: {}",
                    message.getId(), ex.getMessage(), ex);
            handleFailure(message, ex.getMessage());
        }
    }

    private ErpResponse dispatch(ErpAdapter adapter, OutboxMessage message) {
        return switch (message.getJobCode()) {
            case "STOCK_MOVE" -> adapter.sendInventoryMovement(
                    deserialize(message.getPayload(), MovementDto.class));
            case "INVOICE_SYNC" -> adapter.sendInvoice(
                    deserialize(message.getPayload(), InvoiceDto.class));
            case "MAT_SYNC" -> adapter.sendMaterialCard(
                    deserialize(message.getPayload(), MaterialDto.class));
            case "RECEIPT_SYNC" -> adapter.sendGoodsReceipt(
                    deserialize(message.getPayload(), ReceiptApprovalDto.class));
            case "SHIPMENT_SYNC" -> adapter.sendShipmentDispatch(
                    deserialize(message.getPayload(), ShipmentDispatchDto.class));
            default -> ErpResponse.failure("UNKNOWN_JOB",
                    "No handler for jobCode: " + message.getJobCode());
        };
    }

    private void handleSuccess(OutboxMessage message, ErpResponse response) {
        message.setStatus(OutboxStatus.COMPLETED);
        message.setExternalReference(response.getExternalReference());
        message.setErrorMessage(null);
        outboxMessageRepository.save(message);
        logSyncService.onOutboxSuccess(message, response);
        log.info("[OutboxProcessor] SUCCESS: id={}, externalRef={}",
                message.getId(), response.getExternalReference());
    }

    private void handleFailure(OutboxMessage message, String errorMessage) {
        int previousRetryCount = message.getRetryCount();
        int newRetryCount = previousRetryCount + 1;
        message.setRetryCount(newRetryCount);
        message.setErrorMessage(errorMessage);

        if (retryPolicy.isExhausted(newRetryCount)) {
            markPermanentFailure(message, errorMessage);
        } else {
            message.setStatus(OutboxStatus.FAILED);
            // Policy sözleşmesi gereği HENÜZ ARTIRILMAMIŞ sayaç geçirilir:
            // 1. hata → 2^0 = +1 dk, 2. hata → 2^1 = +2 dk.
            // (Önceki kod artırılmış sayacı geçirdiğinden ilk backoff +2 dk
            // oluyor ve dokümante edilen +1 dk adımı hiç yaşanmıyordu.)
            message.setNextAttemptAt(retryPolicy.nextAttemptAt(previousRetryCount));
            outboxMessageRepository.save(message);
            logSyncService.onOutboxRetryableFailure(message);
            log.warn("[OutboxProcessor] FAILED (will retry): id={}, retryCount={}, nextAttemptAt={}",
                    message.getId(), newRetryCount, message.getNextAttemptAt());
        }
    }

    private void markPermanentFailure(OutboxMessage message, String errorMessage) {
        message.setStatus(OutboxStatus.FAILED_MAX_RETRIES);
        message.setErrorMessage(errorMessage);
        outboxMessageRepository.save(message);
        logSyncService.onOutboxPermanentFailure(message);
        alertService.alertMaxRetriesExceeded(message);
        log.error("[OutboxProcessor] PERMANENT FAILURE: id={}, retryCount={}, error={}",
                message.getId(), message.getRetryCount(), errorMessage);
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialize Outbox payload to " + type.getSimpleName(), ex);
        }
    }
}
