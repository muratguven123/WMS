package com.wms.core.dto.ui;


/**
 * Kural motoruna iletilen çalışma zamanı bağlamı (immutable).
 *
 * <p>Her alan nullable'dır — null değer "bu boyut belirtilmedi" anlamına gelir.
 * Kural motoru, null gelen boyutları "herkes/her durum için geçerli" kurallarla
 * eşleştirirken görmezden gelir.</p>
 *
 * <p>Frontend bu nesneyi form yüklenirken header/token'dan türetip
 * {@code GET /api/ui/screens/{screenCode}/schema?...} isteğiyle gönderir.</p>
 *
 * @param locationId    Kullanıcının aktif lokasyonu
 * @param roleId        Kullanıcının aktif rolü
 * @param companyId     Kullanıcının bağlı olduğu şirket
 * @param countryId     Şirketin kayıtlı olduğu ülke (vergi/adres kuralları için)
 * @param operationType Form operasyon tipi — örn: {@code CREATE}, {@code EDIT}, {@code VIEW}
 */
public record UiContext(
        Long locationId,
        Long roleId,
        Long companyId,
        Long countryId,
        String operationType
) {

    /**
     * {@link com.wms.core.security.TenantContext}'ten türetilmiş temel bağlam.
     * Ülke ve operasyon tipi bilinmiyorsa bu factory kullanılır.
     */
    public static UiContext ofTenant(Long locationId, Long companyId) {
        return new UiContext(locationId, null, companyId, null, null);
    }

    /**
     * Redis cache key parçası — tüm bağlam boyutlarını içerir.
     * Null değerler {@code "x"} ile temsil edilir.
     */
    public String cacheKeySuffix() {
        return uuidStr(locationId) + ":"
                + uuidStr(roleId) + ":"
                + uuidStr(companyId) + ":"
                + uuidStr(countryId) + ":"
                + (operationType != null && !operationType.isBlank() ? operationType : "x");
    }

    private static String uuidStr(Long uuid) {
        return uuid != null ? uuid.toString() : "x";
    }
}
