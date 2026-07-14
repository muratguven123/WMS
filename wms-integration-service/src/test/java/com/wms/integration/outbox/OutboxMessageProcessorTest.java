package com.wms.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.ErpAdapterFactory;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.OutboxMessageRepository;
import com.wms.integration.service.IntegrationLogSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OutboxMessageProcessor} — retry, backoff ve kalıcı hata (FAILED_MAX_RETRIES) testleri.
 *
 * <h3>Kurgu</h3>
 * <ul>
 *   <li>{@link OutboxRetryPolicy} GERÇEK instance'tır (maxAttempts=3) — processor ile
 *       policy'nin sayaç sözleşmesi (artırılmamış retryCount) uçtan uca doğrulanır.</li>
 *   <li>ERP adapter'ı her çağrıda {@code RuntimeException} fırlatır — sürekli hata simülasyonu.</li>
 *   <li>Payload {@code "null"} JSON literal'idir: deserializasyon DTO creator
 *       konfigürasyonundan bağımsız başarılı olur ve akış her zaman adapter'a ulaşır.</li>
 *   <li>Repository aynı entity instance'ını döndürür → retryCount, ardışık
 *       process() çağrıları boyunca gerçekçi şekilde birikir.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxMessageProcessor — retry ve kalıcı hata akışı")
class OutboxMessageProcessorTest {

    private static final Long MESSAGE_ID  = 1L;
    private static final Long LOCATION_ID = 1L;

    @Mock private OutboxMessageRepository outboxMessageRepository;
    @Mock private ErpAdapterFactory erpAdapterFactory;
    @Mock private ErpAdapter erpAdapter;
    @Mock private OutboxAlertService alertService;
    @Mock private IntegrationLogSyncService logSyncService;
    @Mock private com.wms.integration.repository.LocationIntegrationConfigRepository configRepository;
    @Mock private com.wms.integration.outbox.WebhookDispatcher webhookDispatcher;

    private OutboxMessageProcessor processor;
    private OutboxMessage message;

    @BeforeEach
    void setUp() {
        // Gerçek policy: maxAttempts=3, cap=60 dk
        OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy();
        setField(retryPolicy, "maxAttempts", 3);
        setField(retryPolicy, "maxBackoffMinutes", 60);

        processor = new OutboxMessageProcessor(
                outboxMessageRepository, erpAdapterFactory, retryPolicy,
                alertService, logSyncService, new ObjectMapper(),
                configRepository, webhookDispatcher);

        message = OutboxMessage.builder()
                .jobCode("STOCK_MOVE")
                .payload("null")            // JSON null literal — creator'dan bağımsız deserialize
                .locationId(LOCATION_ID)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        message.setId(MESSAGE_ID);

        org.mockito.Mockito.lenient().when(outboxMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        org.mockito.Mockito.lenient().when(erpAdapterFactory.getAdapter(LOCATION_ID)).thenReturn(erpAdapter);
        org.mockito.Mockito.lenient().when(erpAdapter.sendInventoryMovement(any()))
                .thenThrow(new RuntimeException("ERP connection refused"));
        org.mockito.Mockito.lenient().when(configRepository.findActiveByLocationId(any()))
                .thenReturn(Optional.empty());
    }

    // =====================================================================
    // Backoff ilerleyişi — 1. hata +1 dk, 2. hata +2 dk
    // =====================================================================

    @Test
    @DisplayName("İlk iki hata: status FAILED, nextAttemptAt +1 dk sonra +2 dk (2^retryCount)")
    void consecutiveFailures_scheduleExponentialBackoff() {
        // --- 1. deneme: retryCount 0 → 1, backoff 2^0 = +1 dk
        OffsetDateTime before1 = OffsetDateTime.now();
        processor.process(MESSAGE_ID);
        OffsetDateTime after1 = OffsetDateTime.now();

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getErrorMessage()).isEqualTo("ERP connection refused");
        assertThat(message.getNextAttemptAt())
                .isAfterOrEqualTo(before1.plusMinutes(1))
                .isBeforeOrEqualTo(after1.plusMinutes(1));

        // --- 2. deneme: retryCount 1 → 2, backoff 2^1 = +2 dk
        OffsetDateTime before2 = OffsetDateTime.now();
        processor.process(MESSAGE_ID);
        OffsetDateTime after2 = OffsetDateTime.now();

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getRetryCount()).isEqualTo(2);
        assertThat(message.getNextAttemptAt())
                .isAfterOrEqualTo(before2.plusMinutes(2))
                .isBeforeOrEqualTo(after2.plusMinutes(2));

        // Henüz kalıcı hata yok — alert tetiklenmemeli
        verifyNoInteractions(alertService);
        verify(logSyncService, times(2)).onOutboxRetryableFailure(message);
    }

    // =====================================================================
    // Senaryo 3 — 3. denemede FAILED_MAX_RETRIES + alert
    // =====================================================================

    @Test
    @DisplayName("3 ardışık hata: mesaj FAILED_MAX_RETRIES olur, alertMaxRetriesExceeded tetiklenir")
    void thirdFailure_marksPermanentFailureAndTriggersAlert() {
        processor.process(MESSAGE_ID);   // retryCount → 1 (FAILED)
        processor.process(MESSAGE_ID);   // retryCount → 2 (FAILED)
        processor.process(MESSAGE_ID);   // retryCount → 3 = maxAttempts → kalıcı hata

        // Mesaj kalıcı hata statüsüne çekildi
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED_MAX_RETRIES);
        assertThat(message.getRetryCount()).isEqualTo(3);

        // Adapter gerçekten 3 kez denendi
        verify(erpAdapter, times(3)).sendInventoryMovement(any());

        // Alert tam 1 kez, doğru mesajla tetiklendi (ArgumentCaptor)
        ArgumentCaptor<OutboxMessage> alertCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(alertService, times(1)).alertMaxRetriesExceeded(alertCaptor.capture());
        OutboxMessage alerted = alertCaptor.getValue();
        assertThat(alerted.getId()).isEqualTo(MESSAGE_ID);
        assertThat(alerted.getStatus()).isEqualTo(OutboxStatus.FAILED_MAX_RETRIES);
        assertThat(alerted.getErrorMessage()).isEqualTo("ERP connection refused");

        // Log senkronizasyonu: 2 geçici + 1 kalıcı hata bildirimi
        verify(logSyncService, times(2)).onOutboxRetryableFailure(message);
        verify(logSyncService, times(1)).onOutboxPermanentFailure(message);
    }

    // =====================================================================
    // Webhook Yönlendirme Testi
    // =====================================================================

    @Test
    @DisplayName("Webhook bağlantı tipi olduğunda WebhookDispatcher'a yönlendirir")
    void route_whenWebhookConnectionType_dispatchesToWebhookDispatcher() {
        com.wms.integration.entity.enums.ConnectionType connectionType = com.wms.integration.entity.enums.ConnectionType.WEBHOOK;
        com.wms.integration.entity.LocationIntegrationConfig config = com.wms.integration.entity.LocationIntegrationConfig.builder()
                .locationId(LOCATION_ID)
                .connectionType(connectionType)
                .build();

        when(configRepository.findActiveByLocationId(LOCATION_ID)).thenReturn(Optional.of(config));

        com.wms.integration.adapter.dto.ErpResponse webhookResponse = com.wms.integration.adapter.dto.ErpResponse.success(
                "WEBHOOK-REF-123", "Webhook sync OK");
        when(webhookDispatcher.dispatch(message, config)).thenReturn(webhookResponse);

        processor.process(MESSAGE_ID);

        verify(webhookDispatcher).dispatch(message, config);
        verify(logSyncService).onOutboxSuccess(message, webhookResponse);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(message.getExternalReference()).isEqualTo("WEBHOOK-REF-123");
    }


    // =====================================================================
    // Yardımcı — @Value alanlarını testte doldurur
    // =====================================================================

    private static void setField(Object target, String fieldName, int value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
