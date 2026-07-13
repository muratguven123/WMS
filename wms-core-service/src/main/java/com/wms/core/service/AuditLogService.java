package com.wms.core.service;

import com.wms.core.entity.ConfigurationAuditLog;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.repository.ConfigurationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Konfigürasyon değişikliklerini asenkron olarak veritabanına kaydeden servis.
 *
 * <h3>Tasarım kararları</h3>
 * <ul>
 *   <li>{@code @Async("auditLogExecutor")} — Ana işlem thread'ini bloklamaz;
 *       audit log yazımı ayrı thread pool'da ({@code audit-log-*}) gerçekleşir.</li>
 *   <li>{@code @Transactional(propagation = REQUIRES_NEW)} — Audit log yazımı
 *       ana işlemin transaksiyonundan bağımsız commit edilir. Ana işlem başarısız
 *       olsa bile log kaydı korunur; ana işlem başarılı olsa bile log hatası
 *       ana işlemi geri almaz.</li>
 *   <li>Her {@link com.wms.core.event.ConfigChangeEvent.FieldChange} için
 *       ayrı {@link ConfigurationAuditLog} satırı yazılır — sorgulama ve
 *       filtreleme kolaylığı sağlar.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ConfigurationAuditLogRepository auditLogRepository;

    /**
     * {@link ConfigChangeEvent} dinleyicisi.
     *
     * <p>Her değişen alan için bir {@link ConfigurationAuditLog} kaydı oluşturur
     * ve hepsini toplu olarak (saveAll) yazar.</p>
     *
     * @param event JPA listener'dan gelen değişiklik olayı
     */
    @Async("auditLogExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConfigChange(ConfigChangeEvent event) {
        try {
            List<ConfigurationAuditLog> logs = event.getChanges().stream()
                    .map(change -> ConfigurationAuditLog.builder()
                            .entityName(event.getEntityName())
                            .entityId(event.getEntityId())
                            .actionType(event.getActionType())
                            .changedFieldName(change.fieldName())
                            .oldValue(change.oldValue())
                            .newValue(change.newValue())
                            .changedByUserId(event.getChangedByUserId())
                            .build())
                    .toList();

            auditLogRepository.saveAll(logs);

            log.info("[Audit] {} kayıt yazıldı. entity={} entityId={} action={} user={}",
                    logs.size(),
                    event.getEntityName(),
                    event.getEntityId(),
                    event.getActionType(),
                    event.getChangedByUserId());

        } catch (Exception ex) {
            // Audit log hatası ana akışı etkilememeli; sadece loglanır.
            // Production'da buraya alerting/dead-letter mekanizması eklenebilir.
            log.error("[Audit] Log yazımı başarısız! entity={} entityId={} hata={}",
                    event.getEntityName(), event.getEntityId(), ex.getMessage(), ex);
        }
    }
}
