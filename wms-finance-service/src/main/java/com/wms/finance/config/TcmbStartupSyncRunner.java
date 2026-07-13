package com.wms.finance.config;

import com.wms.finance.job.TcmbRateSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Uygulama ayağa kalkarken TCMB güncel kurlarını çeker (opsiyonel).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wms.finance.tcmb.sync-on-startup", havingValue = "true")
public class TcmbStartupSyncRunner implements ApplicationRunner {

    private final TcmbRateSyncJob tcmbRateSyncJob;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[TCMB-SYNC] Startup senkronizasyonu tetiklendi.");
        tcmbRateSyncJob.syncTcmbRates();
    }
}
