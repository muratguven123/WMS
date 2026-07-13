package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.MovementDto;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.outbox.OutboxPublisherService;
import com.wms.integration.repository.IntegrationJobRepository;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Stok hareketi entegrasyon servisi.
 *
 * <h3>Atomik Kayıt Örneği</h3>
 * Bu servis, Outbox Pattern'ın "at-most-once" → "at-least-once" dönüşümünü
 * somutlaştırır:
 *
 * <pre>
 * BEGIN TRANSACTION
 *   1. IntegrationLog kaydı oluşturulur (RETRYING)
 *   2. OutboxMessage kaydı oluşturulur (PENDING)
 * COMMIT
 * </pre>
 *
 * İki kayıt aynı transaction'da commit olur; herhangi bir hata her ikisini de
 * geri alır. Outbox Worker sonraki turda mesajı bulup ERP'ye gönderir.
 *
 * <p>Bu sınıf, wms-core-service'deki gerçek stok hareketi servisiyle aynı
 * pattern'ı izler; ancak bu servis kendi domain nesnesi olmadığından
 * ({@code MovementDto} dışarıdan gelir) yalnızca entegrasyon tarafını yönetir.
 * Gerçek projede wms-core-service'deki {@code InventoryMovementService},
 * hareketi kendi repository'sine + Outbox'a aynı transaction içinde yazar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMovementIntegrationService {

    private final OutboxPublisherService              outboxPublisher;
    private final IntegrationLogRepository            integrationLogRepository;
    private final IntegrationJobRepository            integrationJobRepository;
    private final LocationIntegrationConfigRepository configRepository;
    private final ObjectMapper                        objectMapper;

    /**
     * Stok hareketini atomik olarak Outbox kuyruğuna ve integration log'a yazar.
     *
     * @param movementDto gönderilecek stok hareketi
     * @param locationId  kaynak lokasyon Long
     */
    @Transactional
    public void enqueueMovement(MovementDto movementDto, Long locationId) {
        log.info("[MovementIntegration] Enqueuing movement: movementId={}, locationId={}",
                movementDto.getMovementId(), locationId);

        // 1. IntegrationLog kaydını oluştur (RETRYING: outbox worker henüz denemedi)
        IntegrationLog integrationLog = buildIntegrationLog(movementDto, locationId);
        IntegrationLog savedLog = integrationLogRepository.save(integrationLog);

        // 2. Outbox mesajını aynı transaction içinde yaz (MANDATORY propagation)
        OutboxMessage outboxMessage = outboxPublisher.publishInventoryMovement(
                movementDto, locationId);

        // 3. IntegrationLog ↔ OutboxMessage çapraz referansını güncelle
        savedLog.setOutboxMessageId(outboxMessage.getId());
        integrationLogRepository.save(savedLog);

        log.info("[MovementIntegration] Atomic write complete: " +
                 "integrationLogId={}, outboxMessageId={}",
                savedLog.getId(), outboxMessage.getId());

        // COMMIT → her iki kayıt da atomik olarak veritabanında;
        //          OutboxWorker sonraki turda outboxMessage'ı işler.
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private IntegrationLog buildIntegrationLog(MovementDto movementDto, Long locationId) {
        // Lokasyon konfigürasyonu ve iş tanımı çek
        LocationIntegrationConfig config = configRepository
                .findActiveByLocationId(locationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active ERP config for locationId=" + locationId));

        IntegrationJob job = integrationJobRepository
                .findByCodeAndIsActiveTrue("STOCK_MOVE")
                .orElseThrow(() -> new IllegalStateException(
                        "IntegrationJob 'STOCK_MOVE' not found"));

        String requestPayload = serialize(movementDto);

        return IntegrationLog.builder()
                .locationIntegrationConfig(config)
                .integrationJob(job)
                .status(IntegrationStatus.RETRYING)  // Outbox worker henüz denemedi
                .requestPayload(requestPayload)
                .retryCount(0)
                .build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new IllegalStateException("Serialization failed for " + obj.getClass(), ex);
        }
    }
}
