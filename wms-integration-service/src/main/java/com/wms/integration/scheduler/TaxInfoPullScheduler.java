package com.wms.integration.scheduler;

import com.wms.integration.service.TaxInfoPullService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tax-info-pull.enabled", havingValue = "true", matchIfMissing = true)
public class TaxInfoPullScheduler {

    private final TaxInfoPullService taxInfoPullService;

    @Scheduled(cron = "${tax-info-pull.cron:0 30 3 * * *}")
    public void pullTaxInfo() {
        log.info("[TaxInfoPullScheduler] Starting scheduled tax info pull");
        int imported = taxInfoPullService.pullAllActiveLocations();
        log.info("[TaxInfoPullScheduler] Completed. imported={}", imported);
    }
}
