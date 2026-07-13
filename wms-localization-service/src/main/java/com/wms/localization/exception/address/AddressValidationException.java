package com.wms.localization.exception.address;

import lombok.Getter;

import java.util.List;

/**
 * Adres doğrulama hatalarını taşıyan runtime exception.
 *
 * <p>Tek bir doğrulama ihlali için {@link #of(String)} factory metodunu,
 * birden fazla ihlali toplu raporlamak için {@link #ofAll(List)} metodunu kullan.</p>
 *
 * <p>{@code GlobalExceptionHandler} bu exception'ı yakalayarak HTTP 400 döner.</p>
 */
@Getter
public class AddressValidationException extends RuntimeException {

    /**
     * Her biri bir doğrulama ihlalini temsil eden i18n mesaj anahtarları listesi.
     * {@code CountryAddressTemplate.errorMessageKey} değerlerinden doldurulur.
     */
    private final List<String> errorMessageKeys;

    private AddressValidationException(List<String> errorMessageKeys) {
        super("Address validation failed: " + errorMessageKeys);
        this.errorMessageKeys = List.copyOf(errorMessageKeys);
    }

    /**
     * Tek hata anahtarı için kısayol factory.
     */
    public static AddressValidationException of(String errorMessageKey) {
        return new AddressValidationException(List.of(errorMessageKey));
    }

    /**
     * Birden fazla hata anahtarını tek exception'da toplar (fail-all semantiği).
     */
    public static AddressValidationException ofAll(List<String> errorMessageKeys) {
        return new AddressValidationException(errorMessageKeys);
    }
}
