package com.wms.localization.service;

import com.wms.localization.dto.SourceResolution;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Tüm otomatik çeviri işlerini tek kuyrukta yürütür (dil başına sıralı).
 *
 * <p>İki fazlı akış:
 * <ol>
 *   <li><b>Seed</b> — kaynak dilden metinleri anında kopyala (UI hemen kullanılabilir)</li>
 *   <li><b>MT</b> — makine çevirisiyle güncelle; her batch sonrası cache temizlenir</li>
 * </ol>
 */
@Slf4j
@Service
public class LanguageTranslateRunner {

    private static final int SEED_BATCH_SIZE = 500;
    private static final int MT_PERSIST_BATCH = 50;
    private static final String MODULE = "UI";

    private final LanguageRepository languageRepository;
    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationValueRepository translationValueRepository;
    private final MachineTranslationService machineTranslationService;
    private final TranslationService translationService;
    private final TranslationSourceResolver sourceResolver;
    private final Executor i18nExecutor;
    private final TransactionTemplate transactionTemplate;

    public LanguageTranslateRunner(
            LanguageRepository languageRepository,
            TranslationKeyRepository translationKeyRepository,
            TranslationValueRepository translationValueRepository,
            MachineTranslationService machineTranslationService,
            TranslationService translationService,
            TranslationSourceResolver sourceResolver,
            @Qualifier("i18nExecutor") Executor i18nExecutor,
            TransactionTemplate transactionTemplate) {
        this.languageRepository = languageRepository;
        this.translationKeyRepository = translationKeyRepository;
        this.translationValueRepository = translationValueRepository;
        this.machineTranslationService = machineTranslationService;
        this.translationService = translationService;
        this.sourceResolver = sourceResolver;
        this.i18nExecutor = i18nExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public void schedule(String langCode, boolean force) {
        schedule(langCode, force, null);
    }

    public void schedule(String langCode, boolean force, String sourceOverride) {
        String code = langCode.toLowerCase();
        String override = sourceOverride != null ? sourceOverride.toLowerCase() : null;
        i18nExecutor.execute(() -> {
            synchronized (lockKey(code)) {
                try {
                    runTranslate(code, force, override);
                } catch (Exception ex) {
                    log.error("Çeviri işi başarısız → dil={}, force={}, source={}: {}",
                            code, force, override, ex.getMessage(), ex);
                }
            }
        });
    }

    private void runTranslate(String code, boolean force, String sourceOverride) {
        TranslateJob job = transactionTemplate.execute(status -> prepareJob(code, force, sourceOverride));
        if (job == null || job.toTranslate().isEmpty()) {
            log.info("Çevrilecek anahtar yok → dil={}", code);
            translationService.evictCache(code, MODULE);
            return;
        }

        job.pairCounts().forEach((pair, count) ->
                log.info("Çeviri kaynak dağılımı → {}: {} anahtar", pair, count));

        int seeded = transactionTemplate.execute(status -> seedFromSource(job));
        translationService.evictCache(code, MODULE);
        log.info("Seed tamamlandı → dil={}, {} anahtar", code, seeded);

        if (!machineTranslationService.isEnabled()) {
            log.info("MT devre dışı — seed ile tamamlandı → dil={}", code);
            return;
        }

        log.info("Makine çevirisi başlıyor → {} anahtar, hedef={} (force={}, source={})",
                job.toTranslate().size(), code, force, sourceOverride != null ? sourceOverride : "auto");

        Map<String, String> translated = machineTranslationService.translateBatch(
                job.toTranslate(), code, job.index());

        persistMtUpdates(code, translated, job.keysByCode());
        translationService.evictCache(code, MODULE);
        log.info("MT tamamlandı → dil={}, {} anahtar güncellendi", code, translated.size());
    }

    private TranslateJob prepareJob(String code, boolean force, String sourceOverride) {
        Language lang = languageRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Dil bulunamadı: " + code));

        if (lang.isDefault()) {
            log.debug("Varsayılan dil çevrilmez → {}", code);
            return null;
        }

        Language managedLang = languageRepository.findById(lang.getId()).orElse(lang);

        if (force) {
            List<TranslationValue> existing = translationValueRepository
                    .findAllTranslationValuesByLanguageCode(code);
            if (!existing.isEmpty()) {
                translationValueRepository.deleteAll(existing);
                translationValueRepository.flush();
                log.info("Mevcut çeviriler silindi → dil={}, {} kayıt", code, existing.size());
            }
        }

        TranslationSourceIndex index = sourceResolver.buildIndex(MODULE);
        List<TranslationKey> allKeys = translationKeyRepository.findAllByModule(MODULE);
        Set<String> existingKeyCodes = translationValueRepository
                .findAllTranslationValuesByLanguageCode(code)
                .stream()
                .map(tv -> tv.getTranslationKey().getKeyCode())
                .collect(Collectors.toSet());

        Map<String, SourceResolution> toTranslate = new LinkedHashMap<>();
        Map<String, Integer> pairCounts = new LinkedHashMap<>();

        for (TranslationKey key : allKeys) {
            if (!force && existingKeyCodes.contains(key.getKeyCode())) {
                continue;
            }
            index.resolveSourceForKey(key.getKeyCode(), code, sourceOverride)
                    .ifPresent(resolution -> {
                        toTranslate.put(key.getKeyCode(), resolution);
                        String pair = resolution.sourceLang() + "→" + code;
                        pairCounts.merge(pair, 1, Integer::sum);
                    });
        }

        Map<String, TranslationKey> keysByCode = translationKeyRepository
                .findAllByKeyCodeIn(toTranslate.keySet())
                .stream()
                .collect(Collectors.toMap(TranslationKey::getKeyCode, k -> k, (a, b) -> a));

        return new TranslateJob(managedLang, index, toTranslate, pairCounts, keysByCode);
    }

    private int seedFromSource(TranslateJob job) {
        Language managedLang = job.managedLang();
        String code = managedLang.getCode();
        List<TranslationValue> toInsert = new ArrayList<>(SEED_BATCH_SIZE);
        int total = 0;

        for (Map.Entry<String, SourceResolution> entry : job.toTranslate().entrySet()) {
            String text = entry.getValue().text();
            if (text == null || text.isBlank()) {
                continue;
            }
            TranslationKey key = job.keysByCode().get(entry.getKey());
            if (key == null) {
                continue;
            }

            toInsert.add(TranslationValue.builder()
                    .language(managedLang)
                    .translationKey(key)
                    .value(text)
                    .build());
            total++;

            if (toInsert.size() >= SEED_BATCH_SIZE) {
                translationValueRepository.saveAll(toInsert);
                toInsert.clear();
            }
        }

        if (!toInsert.isEmpty()) {
            translationValueRepository.saveAll(toInsert);
        }

        return total;
    }

    private void persistMtUpdates(String code, Map<String, String> translated,
                                  Map<String, TranslationKey> keysByCode) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(translated.entrySet());
        int batchStart = 0;

        while (batchStart < entries.size()) {
            int batchEnd = Math.min(batchStart + MT_PERSIST_BATCH, entries.size());
            List<Map.Entry<String, String>> batch = entries.subList(batchStart, batchEnd);

            transactionTemplate.executeWithoutResult(status -> {
                for (Map.Entry<String, String> entry : batch) {
                    String value = entry.getValue();
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    TranslationKey key = keysByCode.get(entry.getKey());
                    if (key == null) {
                        continue;
                    }
                    translationValueRepository
                            .findByLanguageCodeAndKeyCode(code, entry.getKey())
                            .ifPresentOrElse(
                                    existing -> {
                                        if (!value.equals(existing.getValue())) {
                                            existing.setValue(value);
                                            translationValueRepository.save(existing);
                                        }
                                    },
                                    () -> {
                                        Language lang = languageRepository.findByCode(code).orElseThrow();
                                        translationValueRepository.save(TranslationValue.builder()
                                                .language(lang)
                                                .translationKey(key)
                                                .value(value)
                                                .build());
                                    });
                }
            });

            translationService.evictCache(code, MODULE);
            batchStart = batchEnd;
        }
    }

    private static String lockKey(String code) {
        return ("i18n-translate:" + code).intern();
    }

    private record TranslateJob(
            Language managedLang,
            TranslationSourceIndex index,
            Map<String, SourceResolution> toTranslate,
            Map<String, Integer> pairCounts,
            Map<String, TranslationKey> keysByCode) {}
}
