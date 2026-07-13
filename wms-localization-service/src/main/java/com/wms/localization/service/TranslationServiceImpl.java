package com.wms.localization.service;

import com.wms.localization.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Çeviri servis implementasyonu.
 *
 * Eksik anahtarlar sıralı tek-dil fallback ile doldurulur (TR+EN katmanlı merge yok).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationServiceImpl implements TranslationService {

    private static final String CACHE_KEY_PATTERN = "translations:%s:%s";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TranslationSourceResolver sourceResolver;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, String> getTranslations(String locale, String module) {
        String cacheKey = buildCacheKey(locale, module);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Map<?, ?> cachedMap) {
                log.debug("Cache HIT → key={}", cacheKey);
                return (Map<String, String>) cachedMap;
            }
        } catch (Exception ex) {
            log.warn("Redis cache okunamadı → key={}: {}", cacheKey, ex.getMessage());
        }

        log.debug("Cache MISS → key={}, DB'den yükleniyor", cacheKey);

        Map<String, String> result = loadFromDatabase(locale, module);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    result,
                    RedisConfig.TTL_TRANSLATIONS.toSeconds(),
                    TimeUnit.SECONDS);
            log.info("Çeviri paketi cache'e yazıldı → key={}, {} anahtar", cacheKey, result.size());
        } catch (Exception ex) {
            log.warn("Redis cache yazılamadı → key={}: {}", cacheKey, ex.getMessage());
        }
        return result;
    }

    @Override
    public void evictCache(String locale, String module) {
        try {
            String cacheKey = buildCacheKey(locale, module);
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Cache temizlendi → key={}, silindi={}", cacheKey, deleted);
        } catch (Exception ex) {
            log.warn("Redis cache temizlenemedi ({}:{}): {}", locale, module, ex.getMessage());
        }
    }

    @Override
    public void evictAllCache() {
        try {
            Set<String> keys = redisTemplate.keys("translations:*");
            if (keys != null && !keys.isEmpty()) {
                Long count = redisTemplate.delete(keys);
                log.info("Tüm çeviri cache'i temizlendi → {} key silindi", count);
            }
        } catch (Exception ex) {
            log.warn("Redis toplu cache temizlenemedi: {}", ex.getMessage());
        }
    }

    /**
     * Hedef locale öncelikli; eksik anahtarlar kapsama sırasına göre tek dil fallback ile doldurulur.
     */
    private Map<String, String> loadFromDatabase(String locale, String module) {
        String normalizedLocale = locale.toLowerCase(Locale.ROOT);
        TranslationSourceIndex index = sourceResolver.buildIndex(module);
        Set<String> allKeys = sourceResolver.allKeyCodes(module);

        Map<String, String> merged = index.mergeForLocale(normalizedLocale, allKeys);

        log.debug("Merge tamamlandı → lokasyon={}, modül={}, toplam={}",
                normalizedLocale, module, merged.size());

        return Collections.unmodifiableMap(merged);
    }

    private String buildCacheKey(String locale, String module) {
        return String.format(CACHE_KEY_PATTERN,
                locale.toLowerCase(Locale.ROOT),
                module.toUpperCase(Locale.ROOT));
    }
}
