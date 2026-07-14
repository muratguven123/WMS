package com.wms.localization.event;

import com.wms.localization.entity.MissingTemplateLog;
import com.wms.localization.repository.MissingTemplateLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Eksik şablon içeriği denetçisi (MissingTranslationAuditor ile aynı desen).
 *
 * MissingTemplateEvent geldiğinde arka planda (async) çalışır:
 *   - (locale, templateCode) daha önce loglandıysa → hitCount++ ve lastSeenAt güncelle
 *   - İlk kez görüldüyse → yeni kayıt ekle
 *
 * Propagation.REQUIRES_NEW: Auditor'ın transaction'ı render akışından bağımsız.
 * Render başarısız olsa bile log yazılır; tersine, log yazılamazsa render etkilenmez.
 *
 * Race condition notu:
 * Aynı (locale, templateCode) aynı anda iki thread'den gelirse DB unique constraint
 * DataIntegrityViolationException fırlatır. Bu yakalanıp görmezden gelinir —
 * bir thread zaten yazdı, sayaç bir sonraki event'te güncellenir.
 *
 * @Async thread pool: "auditExecutor" — AsyncConfig'de tanımlı.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingTemplateAuditor {

    private final MissingTemplateLogRepository logRepository;

    @Async("auditExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMissingTemplate(MissingTemplateEvent event) {
        String locale       = event.getLocale();
        String templateCode = event.getTemplateCode();
        String channel      = event.getChannel();

        // Yapısal log — merkezi log aggregation (ELK/Loki) için structured field
        log.warn("[MISSING_TEMPLATE] locale={} templateCode={} channel={}",
                 locale, templateCode, channel);

        try {
            Optional<MissingTemplateLog> existing =
                    logRepository.findByLocaleAndTemplateCode(locale, templateCode);

            if (existing.isPresent()) {
                // Mevcut kayıt: sayacı artır
                MissingTemplateLog record = existing.get();
                record.setHitCount(record.getHitCount() + 1);
                record.setLastSeenAt(LocalDateTime.now());
                // resolved=true ise false'a çek — tekrar görüldü demek
                if (record.isResolved()) {
                    record.setResolved(false);
                    record.setResolvedAt(null);
                    log.info("[MISSING_TEMPLATE] Çözümlendi işaretli şablon tekrar görüldü → " +
                             "locale={} templateCode={}", locale, templateCode);
                }
                logRepository.save(record);
            } else {
                // İlk kez: yeni kayıt
                MissingTemplateLog newRecord = MissingTemplateLog.builder()
                        .locale(locale)
                        .templateCode(templateCode)
                        .channel(channel)
                        .firstSeenAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .hitCount(1L)
                        .resolved(false)
                        .build();
                logRepository.save(newRecord);
            }

        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent insert — güvenle yoksay
            log.debug("[MISSING_TEMPLATE] Concurrent insert algılandı, yoksayılıyor → " +
                      "locale={} templateCode={}", locale, templateCode);
        } catch (Exception e) {
            // Audit başarısız olsa bile render akışı etkilenmemeli — sadece logla
            log.error("[MISSING_TEMPLATE] Audit kaydı yazılamadı → locale={} templateCode={} hata={}",
                      locale, templateCode, e.getMessage());
        }
    }
}
