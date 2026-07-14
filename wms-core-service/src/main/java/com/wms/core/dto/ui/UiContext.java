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
 * <h3>Cache Key Migration Notu (İş İsteri 2.1, Madde 8.3)</h3>
 * <p>{@link #cacheKeySuffix()} yeni boyutlarla 5 segmentten 9 segmente çıktı.
 * Eski formatta yazılmış cache girdileri yeni kod tarafından asla okunmaz
 * (key'ler birebir farklıdır) — çakışma/stale okuma riski yoktur; eski girdiler
 * TTL ile düşer. Deploy sırasında hemen temizlik için bkz. V23 migration notu:
 * {@code redis-cli --scan --pattern 'ui:*' | xargs -r redis-cli DEL}</p>
 *
 * @param locationId        Kullanıcının aktif lokasyonu
 * @param roleId            Kullanıcının aktif rolü
 * @param companyId         Kullanıcının bağlı olduğu şirket
 * @param countryId         Şirketin kayıtlı olduğu ülke (vergi/adres kuralları için)
 * @param operationType     Form operasyon tipi — örn: {@code CREATE}, {@code EDIT}, {@code VIEW}
 * @param warehouseId       Aktif depo (Location/Zone referansı) — null → belirtilmedi
 * @param customerType      Müşteri tipi — örn: {@code RETAIL}, {@code WHOLESALE}, {@code ECOMMERCE}
 * @param productType       Ürün tipi — örn: {@code STANDARD}, {@code HAZMAT}, {@code COLD_CHAIN}
 * @param transactionStatus İşlem durumu — örn: {@code DRAFT}, {@code APPROVED}, {@code SHIPPED}
 */
public record UiContext(
        Long locationId,
        Long roleId,
        Long companyId,
        Long countryId,
        String operationType,
        Long warehouseId,
        String customerType,
        String productType,
        String transactionStatus
) {

    /**
     * Geriye dönük uyumlu kurucu — yeni boyutlar (depo, müşteri tipi, ürün tipi,
     * işlem durumu) null bırakılır. Mevcut çağrı noktaları ve testler bu imzayı
     * kullanmaya devam edebilir.
     */
    public UiContext(Long locationId, Long roleId, Long companyId,
                     Long countryId, String operationType) {
        this(locationId, roleId, companyId, countryId, operationType,
                null, null, null, null);
    }

    /**
     * {@link com.wms.core.security.TenantContext}'ten türetilmiş temel bağlam.
     * Ülke ve operasyon tipi bilinmiyorsa bu factory kullanılır.
     * İmza geriye dönük uyumludur; yeni boyutlar null döner.
     */
    public static UiContext ofTenant(Long locationId, Long companyId) {
        return new UiContext(locationId, null, companyId, null, null);
    }

    /**
     * Redis cache key parçası — tüm bağlam boyutlarını içerir.
     * Null değerler {@code "x"} ile temsil edilir.
     *
     * <p>Segment sırası sabittir ve değiştirilmemelidir:
     * {@code loc:role:comp:country:op:wh:custType:prodType:txnStatus}</p>
     */
    public String cacheKeySuffix() {
        return uuidStr(locationId) + ":"
                + uuidStr(roleId) + ":"
                + uuidStr(companyId) + ":"
                + uuidStr(countryId) + ":"
                + codeStr(operationType) + ":"
                + uuidStr(warehouseId) + ":"
                + codeStr(customerType) + ":"
                + codeStr(productType) + ":"
                + codeStr(transactionStatus);
    }

    private static String uuidStr(Long uuid) {
        return uuid != null ? uuid.toString() : "x";
    }

    private static String codeStr(String code) {
        return code != null && !code.isBlank() ? code : "x";
    }
}
