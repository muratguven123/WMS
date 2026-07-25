package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.outbox.OutboxPublisherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ErpReceiptIntegrationService} birim testleri.
 *
 * <p>Mal kabul onayının outbox'a doğru locationId ile yazılmasını, warehouseLocationId
 * yokken companyId'ye düşülmesini ve geçersiz payload'ların IllegalArgumentException'a
 * sarılmasını doğrular. Gerçek ObjectMapper + mock OutboxPublisherService — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class ErpReceiptIntegrationServiceTest {

    private final OutboxPublisherService outboxPublisher = mock(OutboxPublisherService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErpReceiptIntegrationService service =
            new ErpReceiptIntegrationService(outboxPublisher, objectMapper);

    private String json(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> receipt(Long receiptId, Long companyId, Long warehouseLocationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("receiptId", receiptId);
        m.put("receiptNumber", "RC-1");
        m.put("companyId", companyId);
        m.put("warehouseLocationId", warehouseLocationId);
        return m;
    }

    @Test
    @DisplayName("warehouseLocationId varsa outbox o locationId ile yazılır")
    void enqueue_usesWarehouseLocationId() {
        service.enqueueReceipt(json(receipt(1L, 100L, 200L)));

        verify(outboxPublisher).saveOutboxMessage(
                eq("Receipt"), eq(1L), eq("RECEIPT_SYNC"), eq(200L), any());
    }

    @Test
    @DisplayName("warehouseLocationId yoksa companyId'ye düşülür")
    void enqueue_fallsBackToCompanyId() {
        service.enqueueReceipt(json(receipt(2L, 100L, null)));

        verify(outboxPublisher).saveOutboxMessage(
                eq("Receipt"), eq(2L), eq("RECEIPT_SYNC"), eq(100L), any());
    }

    @Test
    @DisplayName("locationId ve companyId yoksa hata fırlatır ve outbox'a yazılmaz")
    void enqueue_missingIds_throws() {
        assertThatThrownBy(() -> service.enqueueReceipt(json(receipt(3L, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(outboxPublisher, never()).saveOutboxMessage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("geçersiz JSON IllegalArgumentException'a sarılır")
    void enqueue_invalidJson_throws() {
        assertThatThrownBy(() -> service.enqueueReceipt("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid receipt approval payload");

        verify(outboxPublisher, never()).saveOutboxMessage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("hata mesajı 'Invalid receipt approval payload' ile başlar")
    void enqueue_missingIds_wrappedMessage() {
        assertThatThrownBy(() -> service.enqueueReceipt(json(receipt(4L, null, null))))
                .hasMessageContaining("Invalid receipt approval payload");
        assertThat(true).isTrue();
    }
}
