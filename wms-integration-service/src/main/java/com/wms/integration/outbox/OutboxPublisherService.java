package com.wms.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.InvoiceDto;
import com.wms.integration.adapter.dto.MaterialDto;
import com.wms.integration.adapter.dto.MovementDto;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Outbox mesajlarını mevcut iş transaction'ına katılarak atomik olarak kaydeden servis.
 *
 * <h3>Atomiklik Garantisi</h3>
 * Tüm {@code publish*} metodları {@link Propagation#MANDATORY} ile çalışır:
 * çağıran servisin zaten açık bir transaction'ı olması <b>zorunludur</b>.
 * Bu sayede iş kaydı + Outbox kaydı tek bir commit/rollback kapsamında kalır.
 *
 * <pre>
 * // Çağıran servis (örnek):
 * {@literal @}Transactional                   // ← transaction burada açılır
 * public void recordMovement(MovementRequest req) {
 *     InventoryMovement saved = movementRepo.save(...);  // iş kaydı
 *     outboxPublisher.publishInventoryMovement(          // outbox kaydı (aynı tx)
 *         saved.toMovementDto(), saved.getLocationId());
 * }  // ← her ikisi birlikte commit / rollback
 * </pre>
 *
 * <h3>Payload Serileştirme</h3>
 * DTO'lar Jackson {@link ObjectMapper} ile JSON'a dönüştürülür.
 * Serileştirme hatası {@link OutboxPublishException} olarak sarılır ve
 * çağıran transaction'ı rollback'e zorlar; böylece tutarsız durum oluşmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Stok hareketi için Outbox mesajı yazar.
     * Çağıran metodun aktif bir {@code @Transactional} kapsamı içinde olması gerekir.
     *
     * @param movementDto gönderilecek stok hareketi verisi
     * @param locationId  kaynak lokasyon Long (adapter çözümlemesi için)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage publishInventoryMovement(MovementDto movementDto, Long locationId) {
        return saveOutboxMessage(
                "InventoryMovement",
                movementDto.getMovementId(),
                "STOCK_MOVE",
                locationId,
                movementDto
        );
    }

    /**
     * Malzeme kartı senkronizasyonu için Outbox mesajı yazar.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage publishMaterialCard(MaterialDto materialDto, Long aggregateId, Long locationId) {
        return saveOutboxMessage(
                "MaterialCard",
                aggregateId,
                "MAT_SYNC",
                locationId,
                materialDto
        );
    }

    /**
     * Fatura senkronizasyonu için Outbox mesajı yazar.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage publishInvoice(InvoiceDto invoiceDto, Long aggregateId, Long locationId) {
        return saveOutboxMessage(
                "Invoice",
                aggregateId,
                "INVOICE_SYNC",
                locationId,
                invoiceDto
        );
    }

    // -----------------------------------------------------------------------
    // Generic save (internal)
    // -----------------------------------------------------------------------

    /**
     * Herhangi bir payload için Outbox mesajı oluşturur.
     * Özelleştirilmiş iş senaryoları için doğrudan kullanılabilir.
     *
     * @param aggregateType domain nesne tipi
     * @param aggregateId   domain nesne Long
     * @param jobCode       IntegrationJob kodu
     * @param locationId    lokasyon Long
     * @param payload       serileştirilecek DTO nesnesi
     * @return kaydedilen OutboxMessage
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage saveOutboxMessage(
            String aggregateType,
            Long aggregateId,
            String jobCode,
            Long locationId,
            Object payload) {

        String jsonPayload = serialize(payload);

        OutboxMessage message = OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .jobCode(jobCode)
                .locationId(locationId)
                .payload(jsonPayload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextAttemptAt(OffsetDateTime.now())
                .build();

        OutboxMessage saved = outboxMessageRepository.save(message);
        log.debug("[Outbox] Message queued: id={}, type={}, job={}, locationId={}",
                saved.getId(), aggregateType, jobCode, locationId);
        return saved;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new OutboxPublishException(
                    "Failed to serialize payload for Outbox: " + obj.getClass().getSimpleName(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Inner exception
    // -----------------------------------------------------------------------

    public static class OutboxPublishException extends RuntimeException {
        public OutboxPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
