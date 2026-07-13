package com.wms.localization.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asenkron görev yürütme yapılandırması.
 *
 * İki ayrı executor tanımlanır:
 *
 * 1. auditExecutor — eksik çeviri loglama
 *    - Düşük öncelikli arka plan işi
 *    - Küçük thread pool: audit işi yoğun değil, DB yazma yeterli
 *    - Queue kapasitesi 1000: burst durumunda event'ler birikir, kaybolmaz
 *
 * 2. i18nExecutor — dil seed (LanguageEventListener)
 *    - Yeni dil eklenince binlerce kayıt yazabilir
 *    - Daha geniş queue: uzun işlem tamamlanana kadar bekleyebilir
 *
 * AsyncUncaughtExceptionHandler: @Async metotlar void dönerse exception
 * caller'a propagate edilmez — bu handler ile merkezi loglama yapılır.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    // -------------------------------------------------------------------------
    // auditExecutor — MissingTranslationAuditor için
    // -------------------------------------------------------------------------

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // -------------------------------------------------------------------------
    // i18nExecutor — LanguageEventListener için
    // -------------------------------------------------------------------------

    @Bean("i18nExecutor")
    public Executor i18nExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);   // Aynı anda çok sayıda dil eklenmez
        executor.setThreadNamePrefix("i18n-seed-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);  // Seed işlemi uzun sürebilir
        executor.initialize();
        return executor;
    }

    // -------------------------------------------------------------------------
    // Global uncaught exception handler
    // -------------------------------------------------------------------------

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
            log.error("[ASYNC_ERROR] Metot={} Hata={} Parametreler={}",
                    method.getName(), throwable.getMessage(), params);
    }
}
