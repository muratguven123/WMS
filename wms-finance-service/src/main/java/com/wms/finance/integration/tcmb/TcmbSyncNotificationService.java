package com.wms.finance.integration.tcmb;

/**
 * TCMB senkronizasyon sürecinde oluşan kritik hataları yöneticilere bildiren servis.
 */
public interface TcmbSyncNotificationService {

    /**
     * TCMB XML çekme veya parse hatalarını yöneticilere bildirir.
     *
     * @param errorType hata türü (ör. "NETWORK_ERROR", "PARSE_ERROR")
     * @param detail    hata detayı
     */
    void notifyAdminOnSyncFailure(String errorType, String detail);
}
