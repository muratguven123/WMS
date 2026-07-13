package com.wms.finance.job;

import com.wms.finance.dto.TcmbSyncResponse;
import com.wms.finance.dto.TcmbUpsertResult;
import com.wms.finance.integration.tcmb.TcmbParseException;
import com.wms.finance.integration.tcmb.TcmbParsedRate;
import com.wms.finance.integration.tcmb.TcmbSyncNotificationService;
import com.wms.finance.integration.tcmb.TcmbXmlParser;
import com.wms.finance.service.TcmbRateUpsertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Her gün Türkiye saati 15:35'te TCMB'den güncel döviz kurlarını çekerek
 * {@code exchange_rates} tablosuna upsert eden planlanmış görev.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcmbRateSyncJob {

    private static final String TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final int FETCH_FALLBACK_DAYS = 5;

    private final RestTemplate tcmbRestTemplate;
    private final TcmbXmlParser tcmbXmlParser;
    private final TcmbSyncNotificationService notificationService;
    private final TcmbRateUpsertService tcmbRateUpsertService;

    @Scheduled(cron = "0 35 15 * * *", zone = "Europe/Istanbul")
    public void syncTcmbRates() {
        syncTcmbRatesWithResult();
    }

    public TcmbSyncResponse syncTcmbRatesWithResult() {
        log.info("[TCMB-SYNC] Kur senkronizasyonu başladı.");

        String xml = fetchXmlWithFallback();
        if (xml == null) {
            return TcmbSyncResponse.failed();
        }

        List<TcmbParsedRate> parsedRates = parseXml(xml);
        if (parsedRates == null || parsedRates.isEmpty()) {
            return TcmbSyncResponse.failed();
        }

        TcmbUpsertResult upsertResult = tcmbRateUpsertService.upsertRates(parsedRates);
        log.info("[TCMB-SYNC] Senkronizasyon tamamlandı. İşlenen kur adedi: {}", parsedRates.size());
        return TcmbSyncResponse.from(upsertResult);
    }

    /**
     * Belirli bir tarihe ait TCMB verisini manuel olarak çekip senkronize eder.
     */
    public TcmbSyncResponse syncForDate(LocalDate date) {
        String url = buildHistoricalUrl(date);
        log.info("[TCMB-SYNC] Manuel çekim → {}", url);
        try {
            String xml = tcmbRestTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                log.warn("[TCMB-SYNC] {} tarihi için veri bulunamadı.", date);
                return TcmbSyncResponse.failed();
            }
            List<TcmbParsedRate> parsed = tcmbXmlParser.parse(xml);
            if (parsed.isEmpty()) {
                return TcmbSyncResponse.failed();
            }
            return TcmbSyncResponse.from(tcmbRateUpsertService.upsertRates(parsed));
        } catch (Exception ex) {
            log.error("[TCMB-SYNC] Manuel çekim hatası ({}): {}", date, ex.getMessage(), ex);
            return TcmbSyncResponse.failed();
        }
    }

    private String fetchXmlWithFallback() {
        String xml = fetchXml(TCMB_URL);
        if (xml != null) {
            return xml;
        }

        LocalDate today = LocalDate.now(ISTANBUL);
        for (int days = 1; days <= FETCH_FALLBACK_DAYS; days++) {
            LocalDate fallbackDate = today.minusDays(days);
            xml = fetchHistoricalXml(fallbackDate);
            if (xml != null) {
                log.warn("[TCMB-SYNC] today.xml başarısız; {} tarihli kur dosyası kullanılıyor.", fallbackDate);
                return xml;
            }
        }
        return null;
    }

    private String fetchHistoricalXml(LocalDate date) {
        try {
            String xml = tcmbRestTemplate.getForObject(buildHistoricalUrl(date), String.class);
            if (xml == null || xml.isBlank()) {
                return null;
            }
            return xml;
        } catch (RestClientException ex) {
            log.debug("[TCMB-SYNC] {} tarihli kur dosyası alınamadı: {}", date, ex.getMessage());
            return null;
        }
    }

    /** TCMB tarihsel kur dosyası: /kurlar/{yyyy}/{MM}/{ddMMyyyy}.xml */
    static String buildHistoricalUrl(LocalDate date) {
        return String.format(
                "https://www.tcmb.gov.tr/kurlar/%d/%02d/%02d%02d%04d.xml",
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getMonthValue(),
                date.getYear());
    }

    private String fetchXml(String url) {
        try {
            String xml = tcmbRestTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                String msg = "TCMB endpoint boş yanıt döndürdü: " + url;
                log.error("[TCMB-SYNC] {}", msg);
                notificationService.notifyAdminOnSyncFailure("EMPTY_RESPONSE", msg);
                return null;
            }
            return xml;
        } catch (RestClientException ex) {
            String msg = "TCMB endpoint'e ulaşılamadı (" + url + "): " + ex.getMessage();
            log.error("[TCMB-SYNC] Ağ hatası: {}", ex.getMessage(), ex);
            notificationService.notifyAdminOnSyncFailure("NETWORK_ERROR", msg);
            return null;
        }
    }

    private List<TcmbParsedRate> parseXml(String xml) {
        try {
            return tcmbXmlParser.parse(xml);
        } catch (TcmbParseException ex) {
            log.error("[TCMB-SYNC] XML parse hatası: {}", ex.getMessage(), ex);
            notificationService.notifyAdminOnSyncFailure("PARSE_ERROR", ex.getMessage());
            return null;
        }
    }
}
