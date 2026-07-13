package com.wms.core.dto.address;


/**
 * Ülke listesi için hafif DTO — cascade dropdown ilk adımı.
 * Sadece id, isoCode ve name taşır.
 */
public record CountryDto(
        Long   id,
        String isoCode,
        String name
) {}
