package com.wms.core.security.annotation;

import java.lang.annotation.*;

/**
 * Bu annotation ile işaretlenen method veya sınıfta Hibernate tenant filtreleri
 * (companyFilter, locationFilter) aktifleştirilmez.
 *
 * <p>Kullanım alanları:
 * <ul>
 *   <li>Admin paneli — tüm verileri görmesi gereken yönetici işlemleri</li>
 *   <li>Batch/migration job'ları — cross-tenant veri işleme</li>
 *   <li>Raporlama — multi-location aggregate raporlar</li>
 * </ul>
 *
 * <p><b>Dikkat:</b> Bu annotation veri izolasyonunu devre dışı bırakır.
 * Yalnızca yetkili admin işlemlerinde kullanılmalıdır.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreTenantFilter {
}
