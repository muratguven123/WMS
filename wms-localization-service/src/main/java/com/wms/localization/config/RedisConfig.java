package com.wms.localization.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis yapılandırması.
 *
 * İki ayrı TTL stratejisi:
 *   - translations cache : 24 saat (dil paketleri nadiren değişir)
 *   - languages cache    : 1 saat  (aktif dil listesi)
 *
 * Serializasyon: JSON (Jackson) → insan okunabilir, dış araçlarla debug kolaylığı.
 * NOT: Binary (Kryo/JDK) tercih etmiyoruz — farklı servis sürümleri arasında
 *      uyumsuzluk riski taşır.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // -------------------------------------------------------------------------
    // Cache name sabitleri — TranslationService'te bu sabitler kullanılır
    // -------------------------------------------------------------------------
    public static final String CACHE_TRANSLATIONS = "translations";
    public static final String CACHE_LANGUAGES     = "languages";

    /** Default TTL — çeviri paketleri için 24 saat */
    public static final Duration TTL_TRANSLATIONS = Duration.ofHours(24);
    /** Dil listesi için 1 saat */
    public static final Duration TTL_LANGUAGES    = Duration.ofHours(1);

    // -------------------------------------------------------------------------
    // ObjectMapper
    // -------------------------------------------------------------------------

    /**
     * JavaTimeModule eklendi — LocalDateTime serializasyonu için.
     * WRITE_DATES_AS_TIMESTAMPS = false → ISO-8601 string olarak yazar.
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -------------------------------------------------------------------------
    // RedisTemplate — manuel (programmatic) cache erişimi için
    // TranslationService içinde doğrudan kullanılır.
    // -------------------------------------------------------------------------

    /**
     * Key   : String  (örn: "translations:de:UI")
     * Value : Object  (Map<String, String> veya herhangi bir serileştirilebilir tip)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {

        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(connectionFactory);

        var jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    // -------------------------------------------------------------------------
    // CacheManager — @Cacheable / @CacheEvict anotasyon desteği
    // -------------------------------------------------------------------------

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {

        var jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        // Varsayılan konfigürasyon — TTL belirtilmeyen cache'ler için
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // Cache bazında TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put(
                CACHE_TRANSLATIONS,
                defaultConfig.entryTtl(TTL_TRANSLATIONS));

        cacheConfigs.put(
                CACHE_LANGUAGES,
                defaultConfig.entryTtl(TTL_LANGUAGES));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofHours(6)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
