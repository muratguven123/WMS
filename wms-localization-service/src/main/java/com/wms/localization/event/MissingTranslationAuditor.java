package com.wms.localization.event;

import com.wms.localization.entity.MissingTranslationLog;
import com.wms.localization.repository.MissingTranslationLogRepository;
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
 * Eksik çeviri denetçisi.
 *
 * MissingTranslationEvent geldiğinde arka planda (async) çalışır:
 *   - (locale, keyCode) daha önce loglandıysa → hitCount++ ve lastSeenAt güncelle
 *   - İlk kez görüldüyse → yeni kayıt ekle
 *
 * Propagation.REQUIRES_NEW: Auditor'ın transaction'ı ana request'ten bağımsız.
 * Ana request başarısız olsa bile log kaydı yazılır; tersine, log yazılamazsa
 * ana request etkilenmez.
 *
 * Race condition notu:
 * Aynı (locale, keyCode) aynı anda iki thread'den gelirse DB unique constraint
 * DataIntegrityViolationException fırlatır. Bu yakalanıp görmezden gelinir —
 * bir thread zaten yazdı, sayaç bir sonraki event'te güncellenir.
 *
 * @Async thread pool: "auditExecutor" — AsyncConfig'de tanımlı.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingTranslationAuditor {

    private final MissingTranslationLogRepository logRepository;

    @Async("auditExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMissingTranslation(MissingTranslationEvent event) {
        String locale  = event.getLocale();
        String keyCode = event.getKeyCode();
        String module  = event.getModule();

        // Yapısal log — merkezi log aggregation (ELK/Loki) için structured field
        log.warn("[MISSING_TRANSLATION] locale={} keyCode={} module={}", locale, keyCode, module);

        try {
            Optional<MissingTranslationLog> existing =
                    logRepository.findByLocaleAndKeyCode(locale, keyCode);

            if (existing.isPresent()) {
                // Mevcut kayıt: sayacı artır
                MissingTranslationLog record = existing.get();
                record.setHitCount(record.getHitCount() + 1);
                record.setLastSeenAt(LocalDateTime.now());
                // resolved=true ise false'a çek — tekrar görüldü demek
                if (record.isResolved()) {
                    record.setResolved(false);
                    record.setResolvedAt(null);
                    log.info("[MISSING_TRANSLATION] Çözümlendi işaretli anahtar tekrar görüldü → " +
                             "locale={} keyCode={}", locale, keyCode);
                }
                logRepository.save(record);
            } else {
                // İlk kez: yeni kayıt
                MissingTranslationLog newRecord = MissingTranslationLog.builder()
                        .locale(locale)
                        .keyCode(keyCode)
                        .module(module)
                        .firstSeenAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .hitCount(1L)
                        .resolved(false)
                        .build();
                logRepository.save(newRecord);
            }

        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent insert — güvenle yoksay
            log.debug("[MISSING_TRANSLATION] Concurrent insert algılandı, yoksayılıyor → " +
                      "locale={} keyCode={}", locale, keyCode);
        } catch (Exception e) {
            // Audit başarısız olsa bile ana akış etkilenmemeli — sadece logla
            log.error("[MISSING_TRANSLATION] Audit kaydı yazılamadı → locale={} keyCode={} hata={}",
                      locale, keyCode, e.getMessage());
        }
    }
}
