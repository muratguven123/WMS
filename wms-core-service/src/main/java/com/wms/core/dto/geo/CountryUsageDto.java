package com.wms.core.dto.geo;

/**
 * Ülkenin cross-service kullanım özeti (İş İsteri 18 — Teknik İş Kuralı 2).
 *
 * <p>Pasifleştirme öncesi UI bu endpoint'i çağırır ve kullanıcıya
 * "pasifleştirilecek ama mevcut kayıtlar korunacak" onayını gösterir.
 * localization-service'e senkron sorulur; servis ulaşılamazsa
 * {@code checkAvailable=false} döner ve sayılar null kalır (best-effort).</p>
 *
 * @param addressCount   localization'daki adres sayısı (bilinmiyorsa null)
 * @param templateExists ülkenin adres şablonu var mı (bilinmiyorsa null)
 * @param checkAvailable localization servisine ulaşılabildi mi
 */
public record CountryUsageDto(
        Long    countryId,
        Long    addressCount,
        Boolean templateExists,
        boolean checkAvailable
) {}
