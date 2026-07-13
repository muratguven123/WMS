package com.wms.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asenkron işlem konfigürasyonu.
 *
 * <p>Audit log yazımı ana DB işlemini yavaşlatmaması için
 * ayrı bir thread pool üzerinde çalışır.</p>
 *
 * <h3>Pool boyutlandırması</h3>
 * <ul>
 *   <li>corePoolSize=4  : Sürekli hazır thread sayısı</li>
 *   <li>maxPoolSize=10  : Yük altında genişleyebilecek maksimum</li>
 *   <li>queueCapacity=200: Kuyrukta bekleyebilecek görev sayısı</li>
 * </ul>
 * Production'da bu değerler application.yml üzerinden
 * {@code @ConfigurationProperties} ile dışarı taşınabilir.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("audit-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
