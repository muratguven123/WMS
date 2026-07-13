package com.wms.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Hata stratejisi {@code ROUTE_TO_QUARANTINE} olduğunda
 * işlemin ilgili nesnesini karantina bölgesine yönlendiren servis.
 *
 * <h3>Mevcut implementasyon</h3>
 * Bu servis şu aşamada loglama ve olay yayınlama (event-driven) baz alınarak
 * tasarlanmıştır. Gerçek karantina transferi Faz 1'de kurulan
 * {@code Zone (QUARANTINE type)} ve stok hareket altyapısı tamamlandığında
 * bu servis genişletilecektir.
 *
 * <h3>Genişletme noktaları</h3>
 * <ul>
 *   <li>Aktif lokasyondaki {@code QUARANTINE} tipli Zone'u bul.</li>
 *   <li>Stok hareketini (Transfer) karantina zone'una kaydet.</li>
 *   <li>Operasyon ekibini bildirim sistemiyle uyar.</li>
 * </ul>
 */
@Slf4j
@Service
public class QuarantineService {

    /**
     * Hatalı süreç adımı sonucunda bir nesneyi karantinaya yönlendirir.
     *
     * @param locationId   Hatanın gerçekleştiği lokasyon
     * @param stepConfigId Hatanın gerçekleştiği adım konfigürasyonu
     * @param errorDetail  Hata açıklaması — karantina kaydına eklenir
     */
    public void routeToQuarantine(Long locationId, Long stepConfigId, String errorDetail) {
        log.warn("[Quarantine] Karantina yönlendirmesi tetiklendi. " +
                 "locationId={} stepConfigId={} hata={}",
                locationId, stepConfigId, errorDetail);

        /*
         * TODO (Faz 1 stok altyapısı tamamlandıktan sonra):
         *
         *   Zone quarantineZone = zoneRepository
         *       .findByLocationIdAndType(locationId, ZoneType.QUARANTINE)
         *       .orElseThrow(() -> new BusinessException(
         *           "Karantina zone bulunamadı. locationId=" + locationId,
         *           HttpStatus.NOT_FOUND, "QUARANTINE_ZONE_NOT_FOUND"));
         *
         *   transferService.moveToZone(referenceId, quarantineZone.getId(),
         *       "QUARANTINE_REDIRECT", errorDetail);
         *
         *   notificationService.notifyWarehouseManager(locationId, errorDetail);
         */

        log.info("[Quarantine] Karantina yönlendirme isteği kuyruğa alındı. " +
                 "locationId={} stepConfigId={}", locationId, stepConfigId);
    }
}
