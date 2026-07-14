package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.AccountingVoucherDto;
import com.wms.integration.adapter.dto.CountResultDto;
import com.wms.integration.adapter.dto.CustomerAccountDto;
import com.wms.integration.adapter.dto.PurchaseOrderDto;
import com.wms.integration.adapter.dto.ReturnNoticeDto;
import com.wms.integration.adapter.dto.SalesOrderDto;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.outbox.OutboxMessageTypes;
import com.wms.integration.outbox.OutboxPublisherService;
import com.wms.integration.repository.IntegrationJobRepository;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * İş isteri 7.4 — yeni ERP senaryolarının (cari hesap, sipariş, iade, sayım,
 * muhasebe fişi) Outbox kuyruğuna atomik kaydı.
 *
 * <p>{@link InventoryMovementIntegrationService} ile aynı deseni izler:
 * <pre>
 * BEGIN TRANSACTION
 *   1. IntegrationLog kaydı (RETRYING — payload dahil)
 *   2. OutboxMessage kaydı (PENDING)
 *   3. Log ↔ Outbox çapraz referansı
 * COMMIT
 * </pre>
 *
 * Böylece her yeni senaryo da izleme ekranında (IntegrationMonitorController)
 * payload ve hata mesajıyla birlikte görünür.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpScenarioIntegrationService {

    private final OutboxPublisherService              outboxPublisher;
    private final IntegrationLogRepository            integrationLogRepository;
    private final IntegrationJobRepository            integrationJobRepository;
    private final LocationIntegrationConfigRepository configRepository;
    private final ObjectMapper                        objectMapper;

    // -----------------------------------------------------------------------
    // Senaryo bazlı convenience API
    // -----------------------------------------------------------------------

    /** Cari hesap kartını kuyruğa alır. */
    @Transactional
    public void enqueueCustomerAccount(CustomerAccountDto dto) {
        enqueue("CustomerAccount", stableAggregateId(dto.getCustomerCode()),
                OutboxMessageTypes.CUSTOMER_SYNC, dto.getLocationId(), dto);
    }

    /** Satın alma siparişini kuyruğa alır. */
    @Transactional
    public void enqueuePurchaseOrder(PurchaseOrderDto dto) {
        enqueue("PurchaseOrder", stableAggregateId(dto.getOrderNumber()),
                OutboxMessageTypes.PURCHASE_ORDER_SYNC, dto.getLocationId(), dto);
    }

    /** Satış siparişini kuyruğa alır. */
    @Transactional
    public void enqueueSalesOrder(SalesOrderDto dto) {
        enqueue("SalesOrder", stableAggregateId(dto.getOrderNumber()),
                OutboxMessageTypes.SALES_ORDER_SYNC, dto.getLocationId(), dto);
    }

    /** İade bildirimini kuyruğa alır. */
    @Transactional
    public void enqueueReturnNotice(ReturnNoticeDto dto) {
        enqueue("ReturnNotice", stableAggregateId(dto.getReferenceOrderNumber()),
                OutboxMessageTypes.RETURN_SYNC, dto.getLocationId(), dto);
    }

    /** Sayım sonucunu kuyruğa alır. */
    @Transactional
    public void enqueueCountResult(CountResultDto dto) {
        enqueue("CountResult", dto.getCountId(),
                OutboxMessageTypes.COUNT_SYNC, dto.getLocationId(), dto);
    }

    /** Muhasebe fişini kuyruğa alır. */
    @Transactional
    public void enqueueAccountingVoucher(AccountingVoucherDto dto) {
        enqueue("AccountingVoucher",
                stableAggregateId(dto.getVoucherType() + "-" + dto.getVoucherDate()),
                OutboxMessageTypes.VOUCHER_SYNC, dto.getLocationId(), dto);
    }

    // -----------------------------------------------------------------------
    // Generic enqueue (IntegrationLog + Outbox — tek transaction)
    // -----------------------------------------------------------------------

    /**
     * Payload'ı IntegrationLog + Outbox kaydı olarak atomik yazar.
     *
     * @param aggregateType domain nesne tipi (örn. "CustomerAccount")
     * @param aggregateId   domain nesne kimliği (dış kod ise deterministik hash)
     * @param jobCode       {@link OutboxMessageTypes} sabiti
     * @param locationId    kaynak lokasyon ID (adaptör çözümlemesi)
     * @param payload       serileştirilecek DTO
     */
    @Transactional
    public void enqueue(String aggregateType, Long aggregateId,
                        String jobCode, Long locationId, Object payload) {

        log.info("[ErpScenario] Enqueuing: type={}, jobCode={}, locationId={}",
                aggregateType, jobCode, locationId);

        // 1. IntegrationLog kaydı (RETRYING: outbox worker henüz denemedi)
        IntegrationLog savedLog = integrationLogRepository.save(
                buildIntegrationLog(jobCode, locationId, payload));

        // 2. Outbox mesajı — aynı transaction (MANDATORY propagation)
        OutboxMessage outboxMessage = outboxPublisher.saveOutboxMessage(
                aggregateType, aggregateId, jobCode, locationId, payload);

        // 3. Log ↔ Outbox çapraz referansı
        savedLog.setOutboxMessageId(outboxMessage.getId());
        integrationLogRepository.save(savedLog);

        log.info("[ErpScenario] Atomic write complete: integrationLogId={}, outboxMessageId={}",
                savedLog.getId(), outboxMessage.getId());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private IntegrationLog buildIntegrationLog(String jobCode, Long locationId, Object payload) {
        LocationIntegrationConfig config = configRepository
                .findActiveByLocationId(locationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active ERP config for locationId=" + locationId));

        IntegrationJob job = integrationJobRepository
                .findByCodeAndIsActiveTrue(jobCode)
                .orElseThrow(() -> new IllegalStateException(
                        "IntegrationJob '" + jobCode + "' not found"));

        return IntegrationLog.builder()
                .locationIntegrationConfig(config)
                .integrationJob(job)
                .status(IntegrationStatus.RETRYING)   // worker henüz denemedi
                .requestPayload(serialize(payload))
                .retryCount(0)
                .build();
    }

    /**
     * Dış sistem kodları (orderNumber, customerCode vb.) Long ID taşımadığından
     * {@code aggregate_id} kolonu için deterministik, negatif olmayan bir
     * kimlik üretir. Aynı kod her zaman aynı kimliğe eşlenir (izlenebilirlik).
     */
    private Long stableAggregateId(String businessKey) {
        if (businessKey == null) {
            return 0L;
        }
        long h = businessKey.hashCode();
        return h == Long.MIN_VALUE ? 0L : Math.abs(h);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new IllegalStateException("Serialization failed for " + obj.getClass(), ex);
        }
    }
}
