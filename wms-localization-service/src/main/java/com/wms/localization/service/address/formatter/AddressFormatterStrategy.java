package com.wms.localization.service.address.formatter;

import java.util.Map;

/**
 * Ülkeye özgü adres biçimlendirme stratejisi.
 *
 * <p>Her implementasyon bir ülke ISO kodunu işler ve
 * adres bileşenlerinden insan okunabilir tek satırlık metin üretir.</p>
 */
public interface AddressFormatterStrategy {

    /**
     * Bu stratejinin işlediği ISO 3166-1 alpha-2 kodu.
     * Örn: "TR", "US", "DE"
     */
    String isoCode();

    /**
     * Adres bileşenlerini birleştirerek biçimlendirilmiş adres döner.
     *
     * @param details  JSONB'den gelen dinamik alan haritası (key = fieldKey)
     * @param city     şehir
     * @param state    eyalet / il
     * @param zipCode  posta kodu
     * @return boş token içermeyen, temizlenmiş tek satırlık adres metni
     */
    String format(Map<String, Object> details, String city, String state, String zipCode);
}
