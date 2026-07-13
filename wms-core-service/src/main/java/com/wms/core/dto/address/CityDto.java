package com.wms.core.dto.address;


/**
 * Şehir listesi için hafif DTO — cascade dropdown üçüncü adımı.
 */
public record CityDto(
        Long   id,
        String name
) {}
