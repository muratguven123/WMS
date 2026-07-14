package com.wms.core.dto.company;

/**
 * Firmanın sistem genelindeki kullanım özeti.
 *
 * <p>Pasifleştirme öncesi UI bu endpoint'i çağırır; aktif lokasyon veya
 * kullanıcı yetkisi varsa sunucu pasifleştirmeyi 409 ile engeller.</p>
 *
 * @param activeLocationCount aktif depo sayısı
 * @param userAccessCount     atanmış kullanıcı yetkisi sayısı
 * @param transactionLogCount işlem günlüğü kaydı sayısı
 * @param canDeactivate       pasifleştirme güvenli mi (aktif bağımlılık yok)
 */
public record CompanyUsageDto(
        Long    companyId,
        long    activeLocationCount,
        long    userAccessCount,
        long    transactionLogCount,
        boolean canDeactivate
) {}
