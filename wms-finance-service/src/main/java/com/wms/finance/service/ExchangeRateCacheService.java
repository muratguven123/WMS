package com.wms.finance.service;

import com.wms.finance.entity.enums.RateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * {@link CurrencyConversionService} ile uyumlu kur Redis anahtarlarını temizler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateCacheService {

    private static final String CACHE_PREFIX = "rate:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void evict(String sourceCode, String targetCode, LocalDate rateDate) {
        List<String> keys = Arrays.stream(RateType.values())
                .map(type -> buildKey(sourceCode, targetCode, rateDate, type))
                .toList();

        Long deleted = redisTemplate.delete(keys);
        log.debug("[RATE-CACHE] Evicted {} keys for {}→{} @{}", deleted, sourceCode, targetCode, rateDate);
    }

    /**
     * Belirtilen tarihe ait tüm kur cache anahtarlarını temizler (doğrudan + çapraz).
     */
    public void evictAllForDate(LocalDate rateDate) {
        String pattern = CACHE_PREFIX + "*:*:" + rateDate + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Long deleted = redisTemplate.delete(keys);
        log.debug("[RATE-CACHE] Evicted {} keys for date {}", deleted, rateDate);
    }

    private String buildKey(String sourceCode, String targetCode, LocalDate date, RateType type) {
        return CACHE_PREFIX + sourceCode.toUpperCase() + ":" + targetCode.toUpperCase()
                + ":" + date + ":" + type.name();
    }
}
