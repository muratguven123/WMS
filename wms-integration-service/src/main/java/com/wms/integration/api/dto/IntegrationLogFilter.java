package com.wms.integration.api.dto;

import com.wms.integration.entity.enums.IntegrationStatus;
import lombok.Data;


/**
 * GET /api/integrations/logs filtre parametreleri.
 *
 * <p>Spring MVC {@code @ModelAttribute} ile query string'den bind edilir.
 * Her alan opsiyoneldir; null olanlar filtreye dahil edilmez.
 */
@Data
public class IntegrationLogFilter {

    /** Durum filtresi: SUCCESS | FAILED | RETRYING */
    private IntegrationStatus status;

    /**
     * Lokasyon Long filtresi.
     * wms-core-service Location.id ile örtüşür.
     */
    private Long locationId;

    /** IntegrationJob.code filtresi: STOCK_MOVE, INVOICE_SYNC, MAT_SYNC, EX_RATE_PULL */
    private String jobCode;

    /** Prompt 4.4 alias: {@code integrationJobCode} query param → {@link #jobCode} */
    public void setIntegrationJobCode(String integrationJobCode) {
        this.jobCode = integrationJobCode;
    }

    /**
     * Payload içeriklerini (requestPayload, responsePayload) yanıta ekle.
     * Büyük içerikler nedeniyle default false; listeleme performansını korur.
     */
    private boolean includePayloads = false;
}
